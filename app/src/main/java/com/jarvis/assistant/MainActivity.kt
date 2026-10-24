package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF070B12)
private val Panel = Color(0xFF101A27)
private val Cyan = Color(0xFF4DEBFF)
private val Muted = Color(0xFF9BAFC2)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    @Volatile private var speechOutputActive = false
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Film-style full-screen interface: hide status/navigation bars while JARVIS is open.
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        tts = TextToSpeech(this, this)
        setContent { JarvisScreen(::speak, { speechOutputActive }) }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            engine.language = Locale("sk", "SK")
            engine.setSpeechRate(0.96f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speechOutputActive = true }
                override fun onDone(utteranceId: String?) { speechOutputActive = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speechOutputActive = false }
                override fun onError(utteranceId: String?, errorCode: Int) { speechOutputActive = false }
            })
            // Prefer Slovak male voice, especially Lukáš / Neural when the installed TTS engine exposes it.
            // The classic Microsoft Edge "sk-SK-LukasNeural" voice is not guaranteed to be available
            // through Android TextToSpeech; this selects the closest installed Slovak voice.
            val slovakVoices = engine.voices?.filter { it.locale.language == "sk" }.orEmpty()
            val lukasVoice = slovakVoices.firstOrNull {
                val name = (it.name + " " + it.locale.displayName).lowercase(Locale.ROOT)
                name.contains("lukas") || name.contains("lukáš") || name.contains("neural")
            }
            val maleSlovak = slovakVoices.firstOrNull {
                val name = (it.name + " " + it.locale.displayName).lowercase(Locale.ROOT)
                name.contains("male") || name.contains("masculine") || name.contains("man") || name.contains("muž") || name.contains("muz")
            }
            val selectedVoice = lukasVoice ?: maleSlovak
            if (selectedVoice != null) {
                engine.voice = selectedVoice
                android.util.Log.i("JARVIS-TTS", "Selected Slovak voice: ${selectedVoice.name}")
            } else {
                android.util.Log.w("JARVIS-TTS", "No identifiable Slovak male/Lukas voice installed; using Android default Slovak voice. Install/select a Slovak male voice in Android Text-to-speech settings.")
            }
        }
    }
    private fun speak(text: String) {
        val engine = tts ?: return
        // Mark speech synchronously so the microphone loop cannot hear JARVIS speaking.
        speechOutputActive = true
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        if (result == TextToSpeech.ERROR) speechOutputActive = false
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

private data class Message(val jarvis: Boolean, val text: String)

@Composable
private fun JarvisScreen(speak: (String) -> Unit, isSpeaking: () -> Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://api.cloudflare.com/client/v4/accounts/09f7e11cc7eaf76d61294654d547bd8a/ai/v1/chat/completions")?.let { if (it == "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" || it == "https://api.openai.com/v1/chat/completions") "https://api.cloudflare.com/client/v4/accounts/09f7e11cc7eaf76d61294654d547bd8a/ai/v1/chat/completions" else it } ?: "https://api.cloudflare.com/client/v4/accounts/09f7e11cc7eaf76d61294654d547bd8a/ai/v1/chat/completions") }
    var model by remember { mutableStateOf(prefs.getString("model", "@cf/meta/llama-3.1-8b-instruct")?.let { if ((it.startsWith("gemini-") || it == "gpt-4o-mini") && endpoint.contains("api.cloudflare.com/client/v4/accounts/")) "@cf/meta/llama-3.1-8b-instruct" else it } ?: "@cf/meta/llama-3.1-8b-instruct") }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var settings by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var apiTestResult by remember { mutableStateOf("") }
    var apiTesting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var wakeMode by remember { mutableStateOf(false) }
    var appForeground by remember { mutableStateOf(true) }
    var showWorkspace by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(true) }
    var showCheckIn by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var gradeSubject by remember { mutableStateOf("") }
    var gradeValue by remember { mutableStateOf("") }
    var moodValue by remember { mutableFloatStateOf(3f) }
    var moodNote by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4700)
        showIntro = false
        showCheckIn = true
    }
    var lastWorkspaceRequest by remember { mutableStateOf("") }
    var workspaceDescription by remember { mutableStateOf(prefs.getString("workspace_description", "Povedzte JARVISu, čo chcete navrhnúť. Návrh AI sa zobrazí tu a náčrt môžete kresliť prstom.") ?: "") }
    val chat = remember {
        mutableStateListOf<Message>().apply {
            val saved = runCatching { JSONArray(prefs.getString("chat_history", "[]").orEmpty()) }.getOrNull()
            if (saved != null) {
                for (i in 0 until saved.length()) {
                    val item = saved.optJSONObject(i) ?: continue
                    val savedText = item.optString("text").trim()
                    if (savedText.isNotBlank()) add(Message(item.optBoolean("jarvis", true), savedText))
                }
            }
            if (isEmpty()) add(Message(true, "Dobrý deň, pane. JARVIS je pripravený. Systémy sú online."))
        }
    }
    fun handle(raw: String) {
        val text = raw.trim().replace(Regex("(?i)^(?:hej\\s+)?jarvis[,: ]*"), "").trim()
        if (text.isEmpty() || busy) return
        showChat = true
        input = ""
        chat.add(Message(false, text))
        when {
            text.contains("graf", true) || text.contains("známk", true) || text.contains("zlepšil", true) || text.contains("zhoršil", true) || text.contains("mentáln", true) || text.contains("ako som sa mal", true) -> {
                showProgress = true
                val progress = prefs.getString("progress_data", "").orEmpty()
                busy = true
                scope.launch {
                    val prompt = "Odpovedz po slovensky. Použi len tieto uložené záznamy, nevymýšľaj čísla. Vysvetli, čo možno a nemožno vyčítať, zmeny v čase a 1-3 praktické kroky. Ak je údajov málo, povedz to. Používateľ chce zobraziť/priblížiť graf a počuť vysvetlenie. Požiadavka: " + text + "\nZÁZNAMY:\n" + if (progress.isBlank()) "Zatiaľ bez záznamov." else progress
                    val answer = if (key.isBlank()) {
                        if (progress.isBlank()) "Pane, zatiaľ nemám uložené žiadne záznamy. Po dennom prehľade sa tu budú zobrazovať vaše údaje. Na inteligentné vysvetlenie treba pridať API token v Nastaveniach."
                        else "Pane, zobrazil som vaše záznamy. Na porovnanie trendov a hlasové odporúčania pridajte API token v Nastaveniach."
                    } else withContext(Dispatchers.IO) {
                        askAi(endpoint, key, model, chat.toList().takeLast(20) + Message(false, prompt), prefs.getString("memories", "").orEmpty() + "\n" + progress)
                    }
                    chat.add(Message(true, answer)); speak(answer); busy = false
                }
            }
            text.contains("projekt", true) || text.contains("nakresli", true) || text.contains("pracovný priestor", true) || text.contains("navrhni", true) || text.contains("vizualiz", true) || text.contains("premietni", true) || text.contains("ukáž mi v 3d", true) || text.contains("model", true) || text.contains("mapu", true) || text.contains("mapa", true) || text.contains("zobraz map", true) || text.contains("ukáž map", true) || text.contains("cestu okolo", true) || text.contains("trasu", true) || text.contains("okolie školy", true) || text.contains("mozog", true) || text.contains("nemocnicu", true) || text.contains("auto", true) -> {
                showWorkspace = true
                showChat = false
                lastWorkspaceRequest = text
                if (key.isNotBlank()) {
                    val status = "Otváram pracovný priestor a pripravujem návrh, pane."
                    chat.add(Message(true, status)); speak(status)
                    busy = true
                    scope.launch {
                        val progressContext = prefs.getString("memories", "").orEmpty() + "\nUložené osobné záznamy (používaj iba ak relevantné, nevymýšľaj údaje):\n" + prefs.getString("progress_data", "").orEmpty()
                        val answer = withContext(Dispatchers.IO) { askAi(endpoint, key, model, chat.toList().takeLast(20), progressContext) }
                        workspaceDescription = answer
                        prefs.edit().putString("workspace_description", answer).apply()
                        chat.add(Message(true, answer))
                        speak(answer)
                        busy = false
                    }
                } else {
                    val answer = "Otváram pracovný priestor, pane. Nastavte AI kľúč, ak chcete návrh projektu generovať konverzáciou. Náčrt môžete kresliť prstom."
                    workspaceDescription = answer
                    prefs.edit().putString("workspace_description", answer).apply()
                    chat.add(Message(true, answer)); speak(answer)
                }
            }
            Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(\\+|plus|\\-|mínus|krat|krát|delené|/|\\*|×|x)\\s*(-?\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE).find(text) != null -> {
                val match = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(\\+|plus|\\-|mínus|krat|krát|delené|/|\\*|×|x)\\s*(-?\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE).find(text)!!
                val a = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                val op = match.groupValues[2].lowercase(Locale.ROOT)
                val b = match.groupValues[3].replace(',', '.').toDoubleOrNull()
                val result = if (a == null || b == null) null else when (op) {
                    "+", "plus" -> a + b
                    "-", "mínus" -> a - b
                    "*", "x", "×", "krat", "krát" -> a * b
                    "/", "delené" -> if (b == 0.0) null else a / b
                    else -> null
                }
                val answer = when {
                    a == null || b == null -> "Výraz sa mi nepodarilo vypočítať, pane."
                    (op == "/" || op == "delené") && b == 0.0 -> "Nulou sa deliť nedá, pane."
                    result == null -> "Výraz sa mi nepodarilo vypočítať, pane."
                    else -> "$a $op $b = " + String.format(Locale("sk", "SK"), "%.4f", result).trimEnd('0').trimEnd(',')
                }
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("vypni počúvanie", true) || text.contains("prestaň počúvať", true) || text.contains("vypni mikrofón", true) -> {
                wakeMode = false
                listening = false
                val answer = "Hlasové počúvanie vypnuté, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("zapni počúvanie", true) || text.contains("zapni mikrofón", true) -> {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    wakeMode = true
                    listening = true
                    val answer = "Hlasové počúvanie zapnuté. Povedzte Hey Jarvis, pane."
                    chat.add(Message(true, answer)); speak(answer)
                } else {
                    val answer = "Najprv povoľte mikrofón v systéme Android, pane."
                    chat.add(Message(true, answer)); speak(answer)
                    Toast.makeText(context, "Povoľte mikrofón a potom stlačte tlačidlo mikrofónu, pane.", Toast.LENGTH_LONG).show()
                }
            }
            text.contains("vymaž históriu", true) || text.contains("vymaž konverzáciu", true) -> {
                chat.clear()
                val answer = "Históriu konverzácie som vymazal, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("batéria", true) || text.contains("stav batérie", true) -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val answer = if (percent >= 0) "Batéria má $percent percent, pane." else "Stav batérie sa nepodarilo zistiť."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.startsWith("zapamätaj si ", true) || text.startsWith("pamätaj si ", true) || text.startsWith("nauč sa ", true) -> {
                val fact = text.substringAfter(' ').substringAfter(' ').trim()
                val old = prefs.getString("memories", "").orEmpty()
                val updated = (old.lines().filter { it.isNotBlank() } + fact).distinct().takeLast(100).joinToString("\n")
                prefs.edit().putString("memories", updated).apply()
                val answer = "Zapamätal som si to pre ďalšie konverzácie, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("čo si pamätáš", true) || text.contains("moje preferencie", true) || text == "pamäť" -> {
                val memories = prefs.getString("memories", "").orEmpty()
                val answer = if (memories.isBlank()) "Zatiaľ nemám uložené žiadne naučené preferencie, pane. Povedzte napríklad: zapamätaj si, že odpovede chcem stručné." else "Toto si pamätám, pane:\\n" + memories
                chat.add(Message(true, answer)); speak(if (memories.isBlank()) answer else "Zobrazil som uloženú pamäť, pane.")
            }
            text.startsWith("zabudni ", true) -> {
                val query = text.substringAfter(' ').trim()
                val old = prefs.getString("memories", "").orEmpty()
                val remaining = old.lines().filter { it.isNotBlank() && !it.contains(query, ignoreCase = true) }
                prefs.edit().putString("memories", remaining.joinToString("\\n")).apply()
                val answer = "Odstránil som z pamäti položky zodpovedajúce zadaniu, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("ukáž poznámky", true) || text.contains("moje poznámky", true) || text == "poznámky" -> {
                val notes = prefs.getString("notes", "").orEmpty()
                val answer = if (notes.isBlank()) "Zatiaľ nemáte uložené žiadne poznámky, pane." else "Vaše poznámky:\n" + notes
                chat.add(Message(true, answer)); speak(if (notes.isBlank()) answer else "Zobrazil som vaše poznámky, pane.")
            }
            text == "vymaž poznámky" || text == "vymaž všetky poznámky" || text == "zmaž poznámky" -> {
                prefs.edit().remove("notes").apply()
                val answer = "Všetky lokálne poznámky som vymazal, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.startsWith("vymaž poslednú poznámku", true) || text.startsWith("zmaž poslednú poznámku", true) -> {
                val remaining = prefs.getString("notes", "").orEmpty().lines().filter { it.isNotBlank() }.dropLast(1)
                prefs.edit().putString("notes", remaining.joinToString("\\n")).apply()
                val answer = if (remaining.isEmpty()) "Zoznam poznámok je teraz prázdny, pane." else "Poslednú poznámku som odstránil, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.startsWith("naviguj na ", true) || text.startsWith("navigácia na ", true) || text.startsWith("trasa na ", true) -> {
                val destination = text.substringAfter(" na ", "").trim()
                if (destination.isBlank()) {
                    val answer = "Povedzte cieľ, napríklad: naviguj na Poprad, pane."
                    chat.add(Message(true, answer)); speak(answer)
                } else {
                    val answer = "Otváram navigáciu do: $destination, pane."
                    chat.add(Message(true, answer)); speak(answer)
                    runCatching {
                        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(destination, "UTF-8"))
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }.onFailure { Toast.makeText(context, "Navigáciu sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.contains("otvor mapy", true) || text.contains("otvor google maps", true) || text == "mapy" -> {
                val answer = "Otváram mapy, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))) }
                    .onFailure { Toast.makeText(context, "Mapy sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor kalkulačku", true) || text.contains("spusti kalkulačku", true) -> {
                val answer = "Pokúšam sa otvoriť kalkulačku, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    context.startActivity(intent)
                }.onFailure {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=kalkula%C4%8Dka"))) }
                        .onFailure { Toast.makeText(context, "Kalkulačku sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.contains("otvor prehliadač", true) || text.contains("otvor internet", true) -> {
                val answer = "Otváram webový prehliadač, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))) }
                    .onFailure { Toast.makeText(context, "Prehliadač sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor chatgpt", true) || text.contains("spusti chatgpt", true) -> {
                val answer = "Otváram ChatGPT, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com"))) }
                    .onFailure { Toast.makeText(context, "ChatGPT sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.startsWith("otvor aplikáciu ", true) || text.startsWith("spusti aplikáciu ", true) -> {
                val requested = text.substringAfter("aplikáciu ", "").trim()
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val matches = context.packageManager.queryIntentActivities(launcher, 0)
                val match = matches.firstOrNull {
                    it.loadLabel(context.packageManager).toString().contains(requested, ignoreCase = true)
                }
                val launchIntent = match?.let {
                    context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
                }
                if (launchIntent != null) {
                    val appName = match?.loadLabel(context.packageManager)?.toString() ?: requested
                    val answer = "Otváram aplikáciu $appName, pane."
                    chat.add(Message(true, answer)); speak(answer)
                    runCatching { context.startActivity(launchIntent) }
                        .onFailure { Toast.makeText(context, "Aplikáciu sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                } else {
                    val answer = "Aplikáciu s názvom $requested sa mi nepodarilo nájsť, pane."
                    chat.add(Message(true, answer)); speak(answer)
                }
            }
            text.contains("otvor youtube", true) || text.contains("spusti youtube", true) -> {
                val answer = "Otváram YouTube, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))) }
                    .onFailure { Toast.makeText(context, "YouTube sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.startsWith("zahraj ", true) || text.startsWith("prehraj ", true) || text.startsWith("pusti hudbu", true) || text.startsWith("pusti pesničku", true) -> {
                val query = text.substringAfter(' ').let { if (it.equals("hudbu", true)) "hudba" else it }
                val answer = "Hľadám na YouTube: $query"
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")))) }
                    .onFailure { Toast.makeText(context, "Vyhľadávanie hudby sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.startsWith("zavolaj ", true) || text.startsWith("vytoč ", true) -> {
                val number = text.substringAfter(' ').trim().replace(" ", "")
                if (number.isBlank()) {
                    val answer = "Povedzte telefónne číslo, pane."
                    chat.add(Message(true, answer)); speak(answer)
                } else {
                    val answer = "Otváram vytáčanie čísla $number. Hovor potvrďte v telefóne, pane."
                    chat.add(Message(true, answer)); speak(answer)
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
                        .onFailure { Toast.makeText(context, "Vytáčanie sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.startsWith("pripomeň mi ", true) || text.startsWith("pripomeň ", true) || text.startsWith("vytvor udalosť ", true) -> {
                val title = text.substringAfter(' ').substringAfter(' ').trim().ifBlank { "Pripomienka od JARVIS" }
                val answer = "Otváram kalendár na vytvorenie pripomienky: $title. Skontrolujte dátum a uložte ju, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                        .putExtra(android.provider.CalendarContract.Events.TITLE, title))
                }.onFailure {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://calendar.google.com"))) }
                        .onFailure { Toast.makeText(context, "Kalendár sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.startsWith("napíš správu ", true) || text.startsWith("napíš sms ", true) || text.startsWith("vytvor správu ", true) -> {
                val body = text.substringAfter(' ').substringAfter(' ').trim()
                val answer = "Otváram novú SMS s pripraveným textom. Správu skontrolujte a odošlite sami, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                        .putExtra("sms_body", body))
                }.onFailure { Toast.makeText(context, "SMS aplikáciu sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("bezpečnostná kontrola", true) || text.contains("bezpečnosť tabletu", true) || text.contains("ochráň tablet", true) || text.contains("ochrana tabletu", true) || text.contains("bezpečnostný režim", true) -> {
                val answer = "Spúšťam bezpečnostnú kontrolu, pane. Otvorím systémové nastavenia zabezpečenia. Skontrolujte Google Play Protect, aktualizácie Androidu a prístup aplikácií k Prístupnosti, správcovi zariadenia a inštalovaniu neznámych aplikácií. Dôležité: JARVIS zatiaľ nedokáže sám zablokovať inú aplikáciu ani garantovať zastavenie útoku. Neinštalujte podozrivý softvér na osobný tablet.";
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
                    .onFailure { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
            }
            text.contains("pripoj sa na wifi", true) || text.contains("pripoj na wifi", true) || text.contains("otvor wifi", true) || text.contains("nastav wifi", true) || text.contains("zapni wifi", true) || text.contains("wi-fi nastavenia", true) -> {
                val answer = "Otváram nastavenia Wi-Fi, pane. Vyberte svoju sieť a pripojte sa."
                chat.add(Message(true, answer)); speak(answer)
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                }.onFailure {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                }
            }
            text.startsWith("hľadaj ", true) || text.startsWith("vyhľadaj ", true) -> {
                val query = text.substringAfter(' ').trim()
                val answer = "Pane, hľadám na internete: $query"
                chat.add(Message(true, answer)); speak(answer)
                busy = true
                scope.launch {
                    val results = withContext(Dispatchers.IO) { searchWeb(query) }
                    val resultText = if (results.isNotBlank()) {
                        "Výsledky vyhľadávania pre „$query“:\n\n$results"
                    } else {
                        "Pane, priamy výpis výsledkov sa nepodarilo načítať. Otváram vyhľadávanie v prehliadači."
                    }
                    chat.add(Message(true, resultText))
                    if (results.isNotBlank()) speak("Našiel som výsledky. Zobrazujem ich v konverzácii.")
                    else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")))) }
                    busy = false
                }
            }
            text.contains("wifi", true) || text.contains("wi-fi", true) || text.contains("internet", true) -> {
                val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = connectivity.activeNetwork
                val capabilities = connectivity.getNetworkCapabilities(network)
                val connected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                val onWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                val answer = when {
                    !connected -> "Pane, internet nie je dostupný. Ak sa chcete pripojiť, povedzte: Hey Jarvis, pripoj sa na Wi-Fi."
                    onWifi -> "Pane, ste pripojený cez Wi-Fi."
                    else -> "Pane, internet je dostupný cez inú sieť než Wi-Fi."
                }
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("otvor nastavenia", true) || text.contains("nastavenia zariadenia", true) -> {
                val answer = "Otváram nastavenia zariadenia, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor fotoaparát", true) || text.contains("spusti fotoaparát", true) || text.contains("otvor kameru", true) -> {
                val answer = "Otváram fotoaparát, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
                    .onFailure { Toast.makeText(context, "Fotoaparát sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("nastav budík", true) || text.contains("budík na", true) -> {
                val timeMatch = Regex("(?:na\\s+)?(\\d{1,2})[:.]([0-5]\\d)").find(text)
                if (timeMatch == null) {
                    val answer = "Pane, povedzte čas vo formáte hodiny a minúty, napríklad: nastav budík na 7:30."
                    chat.add(Message(true, answer)); speak(answer)
                } else {
                    val hour = timeMatch.groupValues[1].toInt().coerceIn(0, 23)
                    val minute = timeMatch.groupValues[2].toInt().coerceIn(0, 59)
                    val answer = "Otváram nastavenie budíka na %02d:%02d, pane.".format(Locale.ROOT, hour, minute)
                    chat.add(Message(true, answer)); speak(answer)
                    runCatching {
                        context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                            .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "JARVIS")
                            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false))
                    }.onFailure { Toast.makeText(context, "Aplikáciu budíka sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.contains("stopky", true) || text.contains("časovač", true) || text.contains("timer", true) -> {
                val match = Regex("(\\d+)\\s*(minút|minúty|minúta|min|sekúnd|sekundy|sekunda|sek)").find(text)
                if (match == null) {
                    val answer = "Pane, povedzte aj dĺžku, napríklad: spusti časovač na 5 minút."
                    chat.add(Message(true, answer)); speak(answer)
                } else {
                    val amount = match.groupValues[1].toIntOrNull() ?: 1
                    val unit = match.groupValues[2].lowercase(Locale.ROOT)
                    val seconds = if (unit.startsWith("sek")) amount else amount * 60
                    val unitName = if (unit.startsWith("sek")) "sekúnd" else "minút"
                    val answer = "Nastavujem časovač na $amount $unitName, pane."
                    chat.add(Message(true, answer)); speak(answer)
                    runCatching {
                        context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                            .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86400))
                            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "JARVIS")
                            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false))
                    }.onFailure { Toast.makeText(context, "Časovač sa nepodarilo nastaviť.", Toast.LENGTH_SHORT).show() }
                }
            }
            text.contains("koľko je hodín", true) || text.contains("aký je čas", true) -> {
                val answer = "Je " + SimpleDateFormat("H:mm", Locale("sk", "SK")).format(Date()) + "."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("aký je dátum", true) || text.contains("dnešný dátum", true) -> {
                val answer = "Dnes je " + SimpleDateFormat("d. MMMM yyyy", Locale("sk", "SK")).format(Date()) + "."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.startsWith("poznámka ", true) || text.startsWith("zapíš ", true) -> {
                val note = text.substringAfter(' ')
                val old = prefs.getString("notes", "") ?: ""
                prefs.edit().putString("notes", (old + "\n• " + note).trim()).apply()
                val answer = "Poznámku som uložil, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            key.isBlank() -> {
                val answer = "Pane, som v offline režime. Fungujú lokálne príkazy ako čas, dátum, batéria, časovač, poznámky a nastavenia Wi-Fi. Na otvorené otázky a zložité úlohy potrebujem internet a nakonfigurovanú AI, alebo samostatný lokálny AI model."
                chat.add(Message(true, answer)); speak(answer)
            }
            else -> {
                busy = true
                scope.launch {
                    val progress = prefs.getString("progress_data", "").orEmpty()
                    val personalContext = prefs.getString("memories", "").orEmpty() +
                        if (progress.isBlank()) "" else "\nOsobné záznamy známok a dennej pohody (používaj len tieto údaje; nevymýšľaj hodnoty):\n" + progress
                    val answer = withContext(Dispatchers.IO) { askAi(endpoint, key, model, chat.toList().takeLast(20), personalContext) }
                    chat.add(Message(true, answer)); speak(answer); busy = false
                }
            }
        }
    }

    LaunchedEffect(chat.size) {
        val saved = JSONArray()
        chat.takeLast(60).forEach { message ->
            saved.put(JSONObject().put("jarvis", message.jarvis).put("text", message.text))
        }
        prefs.edit().putString("chat_history", saved.toString()).apply()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            wakeMode = true
            listening = true
        } else {
            wakeMode = false
            listening = false
            Toast.makeText(context, "Bez povolenia mikrofónu hlasové ovládanie nefunguje.", Toast.LENGTH_LONG).show()
        }
    }

    // Do not start the microphone at app launch. The user explicitly enables it with the mic button.
    LaunchedEffect(Unit) {
        wakeMode = false
        listening = false
    }

    val activityLifecycle = (context as? ComponentActivity)?.lifecycle
    DisposableEffect(activityLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                appForeground = false
                wakeMode = false
                listening = false
            } else if (event == Lifecycle.Event.ON_START) {
                appForeground = true
            }
        }
        activityLifecycle?.addObserver(observer)
        onDispose { activityLifecycle?.removeObserver(observer) }
    }

    DisposableEffect(wakeMode, appForeground) {
        if (!wakeMode || !appForeground) {
            listening = false
            onDispose { }
        } else {
            listening = true
            val wakeLoop = WakeWordLoop(
                context = context,
                onCommand = { spoken ->
                    // One spoken command per mic activation; stop listening before JARVIS replies.
                    wakeMode = false
                    listening = false
                    handle(spoken)
                },
                onWake = {
                    speak("Počúvam, pane.")
                },
                canListen = { !isSpeaking() && !busy }
            )
            wakeLoop.start()
            onDispose {
                wakeLoop.stop()
                listening = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10263A), Bg, Color(0xFF05070C))))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(48.dp).background(Cyan.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("J", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (busy) "SPRACÚVAM…" else "SYSTÉMY ONLINE • SLOVENČINA", color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    TextButton(onClick = { showWorkspace = !showWorkspace }) { Text("PROJEKT", color = Cyan, fontSize = 11.sp) }
                    TextButton(onClick = { settings = true }) { Text("⚙", color = Cyan, fontSize = 22.sp) }
                }
                if (!showWorkspace && !showChat) {
                    JarvisDashboard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        wakeMode = wakeMode,
                        busy = busy,
                        aiReady = endpoint.isNotBlank() && key.isNotBlank() && model.isNotBlank(),
                        model = model,
                        onToggleVoice = {
                            if (wakeMode) { wakeMode = false; listening = false }
                            else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { wakeMode = true; listening = true }
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onWorkspace = { showWorkspace = true },
                        onChat = { showChat = true },
                        onSettings = { settings = true }
                    )
                }
                if (showWorkspace) {
                    ProjectWorkspace(title = "JARVIS • Interaktívny návrh", description = workspaceDescription, onClose = { showWorkspace = false }, initialMapMode = lastWorkspaceRequest.contains("premiet", true) || lastWorkspaceRequest.contains("cestu okolo", true) || lastWorkspaceRequest.contains("trasu", true) || lastWorkspaceRequest.contains("mapu", true) || lastWorkspaceRequest.contains("okolie školy", true), onRefine = { instruction ->
                        if (key.isNotBlank() && !busy) {
                            busy = true
                            chat.add(Message(false, instruction))
                            scope.launch {
                                val projectContext = prefs.getString("memories", "").orEmpty() + "\nUložené osobné záznamy (iba ak relevantné):\n" + prefs.getString("progress_data", "").orEmpty()
                                val answer = withContext(Dispatchers.IO) { askAi(endpoint, key, model, (chat.toList().takeLast(20) + Message(false, "Uprav aktívny návrh podľa tejto požiadavky: $instruction. Zachovaj užitočné časti a vysvetli konkrétne zmeny.")), projectContext) }
                                workspaceDescription = answer
                                prefs.edit().putString("workspace_description", answer).apply()
                                chat.add(Message(true, answer)); speak(answer); busy = false
                            }
                        } else if (key.isBlank()) {
                            val msg = "Pane, na úpravy návrhu potrebujem nastavený AI kľúč."
                            chat.add(Message(true, msg)); speak(msg)
                        }
                    })
                    Spacer(Modifier.height(10.dp))
                }
                if (!showWorkspace && showChat) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("KONVERZÁCIA", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    TextButton(onClick = { showChat = false }) { Text("DOMOV", color = Cyan) }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(chat) { message ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.jarvis) Arrangement.Start else Arrangement.End) {
                            Column(Modifier.fillMaxWidth(.88f).background(if (message.jarvis) Panel else Color(0xFF123346), RoundedCornerShape(15.dp)).padding(12.dp)) {
                                Text(if (message.jarvis) "JARVIS" else "PANE", color = if (message.jarvis) Cyan else Color(0xFFB9D8FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(message.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                    if (busy) item { Text("JARVIS pripravuje odpoveď…", color = Muted, fontSize = 12.sp) }
                }
                }
                if (!showWorkspace && showChat) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Čo si želáte, pane?", color = Muted) }, singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { handle(input) }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2C455A), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    IconButton(onClick = {
                        if (wakeMode) {
                            wakeMode = false
                            listening = false
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            wakeMode = true
                            listening = true
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(if (wakeMode) "🎙" else "🎤", fontSize = 22.sp) }
                    Button(onClick = { handle(input) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) { Text("➤", fontSize = 18.sp) }
                }
                Text(if (wakeMode) "MIKROFÓN AKTÍVNY • povedzte príkaz." else "MIKROFÓN VYPNUTÝ • stlačte 🎤, povedzte príkaz a JARVIS ho vypne.", color = Muted, fontSize = 10.sp)
                }
            }
            if (showIntro) {
                JarvisIntro(onSkip = { showIntro = false })
            }
        }
        if (showProgress && !showIntro) {
            val entries = prefs.getString("progress_data", "").orEmpty().lines().filter { it.isNotBlank() }
            val grades = entries.mapNotNull { line ->
                val parts = line.split(" | ")
                if (parts.size >= 4 && parts[1] == "ZNÁMKA") parts[0] to (parts[3].toFloatOrNull()) else null
            }
            val moods = entries.mapNotNull { line ->
                val parts = line.split(" | ")
                if (parts.size >= 4 && parts[1] == "DEŇ") parts[0] to (parts[2].substringBefore("/").toFloatOrNull()) else null
            }
            AlertDialog(
                onDismissRequest = { showProgress = false },
                title = { Text("OSOBNÉ GRAFY • JARVIS") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ZNÁMKY (1 = najlepšia, 5 = najslabšia)", color = Cyan, fontSize = 12.sp)
                        if (grades.isEmpty()) Text("Zatiaľ nie sú uložené známky.", color = Muted)
                        else {
                            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                                val values = grades.takeLast(12).map { it.second ?: 0f }
                                if (values.size > 1) {
                                    val stepX = size.width / (values.size - 1)
                                    for (i in 0 until values.lastIndex) {
                                        val y1 = (values[i] / 5f) * size.height
                                        val y2 = (values[i + 1] / 5f) * size.height
                                        drawLine(Cyan, Offset(i * stepX, y1), Offset((i + 1) * stepX, y2), 3.dp.toPx())
                                    }
                                }
                            }
                            grades.takeLast(8).forEach { Text(it.first + " • " + it.second?.toInt(), color = Muted, fontSize = 11.sp) }
                        }
                        Text("AKO SOM SA MAL (1–5)", color = Cyan, fontSize = 12.sp)
                        if (moods.isEmpty()) Text("Zatiaľ nie sú uložené hodnotenia dní.", color = Muted)
                        else {
                            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                                val values = moods.takeLast(14).map { it.second ?: 0f }
                                if (values.size > 1) {
                                    val stepX = size.width / (values.size - 1)
                                    for (i in 0 until values.lastIndex) {
                                        val y1 = size.height - (values[i] / 5f) * size.height
                                        val y2 = size.height - (values[i + 1] / 5f) * size.height
                                        drawLine(Color(0xFF75E6A5), Offset(i * stepX, y1), Offset((i + 1) * stepX, y2), 3.dp.toPx())
                                    }
                                }
                            }
                            moods.takeLast(8).forEach { Text(it.first + " • " + it.second?.toInt() + "/5", color = Muted, fontSize = 11.sp) }
                        }
                        Text("Grafy vychádzajú iba z uložených záznamov. Sú orientačné, nie diagnostické.", color = Muted, fontSize = 10.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showProgress = false }) { Text("Zavrieť") } },
                dismissButton = { TextButton(onClick = { handle("vysvetli moje grafy a odporuč zlepšenie") }) { Text("Vysvetliť hlasom") } }
            )
        }
        if (showCheckIn && !showIntro) {
            AlertDialog(
                onDismissRequest = { showCheckIn = false },
                title = { Text("Denný prehľad JARVIS") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dostali ste dnes nové známky? A ako ste sa dnes mali? Údaje sa ukladajú iba v tomto zariadení.", color = Muted, fontSize = 13.sp)
                        OutlinedTextField(value = gradeSubject, onValueChange = { gradeSubject = it }, label = { Text("Predmet (napr. Matematika)") }, singleLine = true)
                        OutlinedTextField(value = gradeValue, onValueChange = { gradeValue = it.filter { c -> c.isDigit() }.take(1) }, label = { Text("Známka 1–5 (voliteľné)") }, singleLine = true)
                        Text("Ako ste sa dnes mali? 1 = ťažký deň, 5 = veľmi dobrý deň", color = Muted, fontSize = 12.sp)
                        Slider(value = moodValue, onValueChange = { moodValue = it }, valueRange = 1f..5f, steps = 3)
                        Text("Hodnotenie dňa: " + moodValue.toInt() + "/5", color = Cyan)
                        OutlinedTextField(value = moodNote, onValueChange = { moodNote = it }, label = { Text("Poznámka (voliteľné)") }, minLines = 1, maxLines = 2)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val entries = prefs.getString("progress_data", "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
                        if (gradeSubject.isNotBlank() && gradeValue.toIntOrNull() in 1..5) {
                            entries.add(date + " | ZNÁMKA | " + gradeSubject.trim() + " | " + gradeValue.toInt())
                        }
                        entries.add(date + " | DEŇ | " + moodValue.toInt() + "/5 | " + moodNote.trim().take(220))
                        prefs.edit().putString("progress_data", entries.takeLast(120).joinToString("\n")).apply()
                        showCheckIn = false
                        val msg = "Denný prehľad uložený, pane. Pri analýze použijem iba skutočne zaznamenané údaje."
                        chat.add(Message(true, msg))
                        speak(msg)
                    }) { Text("Uložiť") }
                },
                dismissButton = { TextButton(onClick = { showCheckIn = false }) { Text("Preskočiť dnes") } }
            )
        }
        if (settings) {
            AlertDialog(
                onDismissRequest = { settings = false },
                title = { Text("Nastavenia JARVIS") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("API kľúč sa ukladá v zariadení. Nevkladajte ho do GitHubu.", color = Muted, fontSize = 12.sp)
                        Text("Predvolená služba: Cloudflare Workers AI • model Llama 3.1 8B", color = Cyan, fontSize = 12.sp)
                        OutlinedTextField(key, { key = it }, label = { Text("Cloudflare API token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Button(
                            onClick = {
                                if (key.isBlank()) {
                                    apiTestResult = "Najprv zadajte API token."
                                } else {
                                    apiTesting = true
                                    apiTestResult = "Testujem spojenie s AI…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            askAi(endpoint.trim(), key.trim(), model.trim(),
                                                listOf(Message(false, "Odpovedz presne: JARVIS ONLINE")),
                                                prefs.getString("memories", "").orEmpty())
                                        }
                                        apiTestResult = if (result.contains("JARVIS ONLINE", true) && !result.contains("zlyhalo", true) && !result.contains("odmietla", true) && !result.contains("neexistuje", true)) {
                                            "Spojenie funguje. Odpoveď: ${result.take(180)}"
                                        } else {
                                            "Test sa nepodaril: ${result.take(240)}"
                                        }
                                        apiTesting = false
                                    }
                                }
                            },
                            enabled = !apiTesting
                        ) { Text(if (apiTesting) "TESTUJEM…" else "OTESTOVAŤ AI") }
                        if (apiTestResult.isNotBlank()) {
                            Text(apiTestResult, color = if (apiTestResult.startsWith("Spojenie funguje")) Cyan else Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showAdvancedSettings = !showAdvancedSettings }) {
                            Text(if (showAdvancedSettings) "Skryť pokročilé nastavenia" else "Pokročilé nastavenia", color = Muted)
                        }
                        if (showAdvancedSettings) {
                            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API endpoint") }, singleLine = true)
                            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = {
                    prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim()).putString("key", key.trim()).apply()
                    settings = false
                    Toast.makeText(context, "Nastavenia uložené.", Toast.LENGTH_SHORT).show()
                }) { Text("Uložiť") } },
                dismissButton = { TextButton(onClick = { settings = false }) { Text("Zrušiť") } }
            )
        }
    }
}


private data class JarvisTask(val title: String, val done: Boolean)

@Composable
private fun JarvisDashboard(
    modifier: Modifier,
    wakeMode: Boolean,
    busy: Boolean,
    aiReady: Boolean,
    model: String,
    onToggleVoice: () -> Unit,
    onWorkspace: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "jarvis_hud")
    val ringRotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "ring_rotation")
    val batteryPercent = remember { (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) }
    val prefs = remember { context.getSharedPreferences("jarvis_dashboard", Context.MODE_PRIVATE) }
    val tasks = remember {
        mutableStateListOf<JarvisTask>().apply {
            val saved = runCatching { JSONArray(prefs.getString("tasks", "[]").orEmpty()) }.getOrNull()
            if (saved != null) for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isNotBlank()) add(JarvisTask(title, item.optBoolean("done", false)))
            }
            if (isEmpty()) {
                add(JarvisTask("Skontrolovať dnešné úlohy", false))
                add(JarvisTask("Pokračovať na projekte JARVIS", false))
            }
        }
    }
    var addDialog by remember { mutableStateOf(false) }
    var taskInput by remember { mutableStateOf("") }
    fun saveTasks() {
        val json = JSONArray()
        tasks.forEach { json.put(JSONObject().put("title", it.title).put("done", it.done)) }
        prefs.edit().putString("tasks", json.toString()).apply()
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OSOBNÝ SYSTÉM • JARVIS", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("Dobrý deň, pane.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(SimpleDateFormat("EEEE • d. MMMM", Locale("sk", "SK")).format(Date()).uppercase(Locale("sk", "SK")), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (busy) "SPRACÚVAM" else "SYSTÉMY ONLINE", color = if (busy) Color(0xFFFFC66D) else Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (aiReady) "AI NASTAVENÁ" else "LOKÁLNY REŽIM", color = Muted, fontSize = 9.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(350.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF174D68), Color(0xFF0B1B2B), Color(0xFF04070C))), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Keep the HUD self-contained: no live webpage underneath the overlay.
            Box(Modifier.fillMaxSize().background(Color(0x99050B12), RoundedCornerShape(24.dp)))
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scale = minOf(size.width, size.height)
                // Layered animated technical rings inspired by the film holographic workshop.
                for (i in 1..9) drawCircle(Cyan.copy(alpha = if (i % 2 == 0) 0.13f else 0.07f), radius = i * scale / 13f, center = Offset(cx, cy), style = Stroke(width = if (i == 3) 1.8.dp.toPx() else 0.8.dp.toPx()))
                drawArc(Cyan.copy(alpha = 0.9f), ringRotation, 62f, false, Offset(cx - scale * .39f, cy - scale * .39f), androidx.compose.ui.geometry.Size(scale * .78f, scale * .78f), style = Stroke(width = 2.dp.toPx()))
                drawArc(Color(0xFF8ACBFF).copy(alpha = .75f), -ringRotation * .7f, 38f, false, Offset(cx - scale * .29f, cy - scale * .29f), androidx.compose.ui.geometry.Size(scale * .58f, scale * .58f), style = Stroke(width = 1.4.dp.toPx()))
                for (i in 0..47) {
                    val a = Math.PI * 2.0 * i / 48.0
                    val r1 = scale * 0.31f
                    val r2 = scale * if (i % 6 == 0) 0.39f else if (i % 3 == 0) 0.36f else 0.345f
                    drawLine(Cyan.copy(alpha = if (i % 3 == 0) 0.75f else 0.22f), Offset(cx + kotlin.math.cos(a).toFloat() * r1, cy + kotlin.math.sin(a).toFloat() * r1), Offset(cx + kotlin.math.cos(a).toFloat() * r2, cy + kotlin.math.sin(a).toFloat() * r2), 1.dp.toPx())
                }
                drawLine(Cyan.copy(alpha = 0.12f), Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())
                drawLine(Cyan.copy(alpha = 0.12f), Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
                drawCircle(Cyan.copy(alpha = 0.18f), radius = 3.dp.toPx(), center = Offset(cx, cy))
                // Small target markers around the central core.
                listOf(Offset(size.width * .16f, size.height * .38f), Offset(size.width * .83f, size.height * .42f), Offset(size.width * .22f, size.height * .72f), Offset(size.width * .78f, size.height * .72f)).forEach {
                    drawCircle(Cyan.copy(alpha = .55f), radius = 3.dp.toPx(), center = it)
                    drawCircle(Cyan.copy(alpha = .2f), radius = 10.dp.toPx(), center = it, style = Stroke(width = 1.dp.toPx()))
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(100.dp).background(Cyan.copy(alpha = .05f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(80.dp).background(Cyan.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(62.dp).background(Brush.radialGradient(listOf(Color(0xFF2B91A8), Color(0xFF081723))), CircleShape), contentAlignment = Alignment.Center) {
                            Text("J", color = Cyan, fontSize = 48.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("J.A.R.V.I.S.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                Text(if (busy) "ANALYZUJEM POŽIADAVKU" else if (wakeMode) "◉ AKTÍVNE POČÚVANIE" else "PRIPRAVENÝ NA POKYN", color = Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
            Column(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                Text("STARK INDUSTRIES", color = Cyan, fontSize = 9.sp, letterSpacing = 1.8.sp)
                Text("NEURAL INTERFACE / MK–01", color = Muted, fontSize = 7.sp)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End) {
                Text(if (aiReady) "AI LINK • TOKEN NASTAVENÝ" else "AI LINK • STANDBY", color = if (aiReady) Color(0xFF69F0AE) else Color(0xFFFFC66D), fontSize = 8.sp)
                Text("VOICE / SK-SK", color = Muted, fontSize = 8.sp)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text("SYSTEM MONITOR", color = Muted, fontSize = 8.sp)
                Text(if (wakeMode) "MICROPHONE • LISTENING" else "MICROPHONE • OFF", color = if (wakeMode) Cyan else Muted, fontSize = 8.sp)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalAlignment = Alignment.End) {
                Text("INTERFACE 02.6", color = Muted, fontSize = 8.sp)
                Text("HOLOGRAPHIC DISPLAY • 01", color = Cyan, fontSize = 7.sp)
            }
            // Floating telemetry directly on the hologram, not in cards.
            Column(Modifier.align(Alignment.CenterStart).padding(start = 12.dp).width(112.dp)) {
                Text("PROGRES ZNÁMOK", color = Cyan, fontSize = 8.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                    val grid = Color(0xFF1C5267)
                    for (i in 0..3) {
                        val y = size.height * i / 3f
                        drawLine(grid.copy(alpha = .55f), Offset(0f, y), Offset(size.width, y), .7.dp.toPx())
                    }
                    drawLine(Cyan.copy(alpha = .7f), Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 1.2.dp.toPx())
                }
                Text("DOPLŇ ZNÁMKY PRE SLEDOVANIE", color = Muted, fontSize = 6.sp, lineHeight = 9.sp)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).width(96.dp), horizontalAlignment = Alignment.End) {
                Text("OSOBNÝ PROGRES", color = Cyan, fontSize = 8.sp, letterSpacing = .8.sp)
                Spacer(Modifier.height(7.dp))
                Text("ÚLOHY  ${tasks.count { it.done }}/${tasks.size}", color = Color.White, fontSize = 10.sp)
                Text("BATÉRIA  ${batteryPercent?.let { "$it%" } ?: "—"}", color = Muted, fontSize = 9.sp)
                Spacer(Modifier.height(7.dp))
                Text("LIVE TELEMETRY", color = Cyan.copy(alpha = .7f), fontSize = 7.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(18.dp)).padding(12.dp)) {
                Text("◉  SATELIT • SVIT", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF07131F))) {
                    val grid = Color(0xFF15394B)
                    val step = 22.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
                    val p = Path().apply {
                        moveTo(-8f, size.height * .2f); lineTo(size.width * .3f, size.height * .35f); lineTo(size.width * .6f, size.height * .2f); lineTo(size.width + 8f, size.height * .45f)
                    }
                    drawPath(p, Color(0xFF2A6075), style = Stroke(width = 2.dp.toPx()))
                    val q = Path().apply {
                        moveTo(size.width * .1f, size.height + 8f); lineTo(size.width * .35f, size.height * .65f); lineTo(size.width * .55f, size.height * .7f); lineTo(size.width * .7f, -8f)
                    }
                    drawPath(q, Cyan.copy(alpha = .9f), style = Stroke(width = 2.5.dp.toPx()))
                    drawCircle(Cyan.copy(alpha = .2f), 13.dp.toPx(), Offset(size.width * .55f, size.height * .57f))
                    drawCircle(Cyan, 3.dp.toPx(), Offset(size.width * .55f, size.height * .57f))
                }
                Spacer(Modifier.height(5.dp))
                Text("SATELITNÝ POHĽAD • SVIT, SLOVENSKO", color = Muted, fontSize = 8.sp)
                TextButton(onClick = onWorkspace, contentPadding = PaddingValues(0.dp)) { Text("Otvoriť mapu ↗", color = Cyan, fontSize = 11.sp) }
            }
            Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(18.dp)).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("☑  MOJE ÚLOHY", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { addDialog = true }, contentPadding = PaddingValues(0.dp)) { Text("+", color = Cyan, fontSize = 20.sp) }
                }
                if (tasks.isEmpty()) Text("Zatiaľ žiadne úlohy.", color = Muted, fontSize = 11.sp)
                tasks.take(4).forEachIndexed { index, task ->
                    Row(Modifier.fillMaxWidth().clickable { tasks[index] = task.copy(done = !task.done); saveTasks() }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { tasks[index] = task.copy(done = it); saveTasks() }, modifier = Modifier.size(26.dp))
                        Text(task.title, color = if (task.done) Muted else Color.White, fontSize = 10.sp, maxLines = 2)
                    }
                }
                Text(tasks.count { it.done }.toString() + " / " + tasks.size + " SPLNENÉ", color = Muted, fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardAction("🎙", if (wakeMode) "Vypnúť hlas" else "Zapnúť hlas", Modifier.weight(1f), onToggleVoice)
            DashboardAction("◈", "Rozhovor", Modifier.weight(1f), onChat)
            DashboardAction("◇", "STARK WORKSHOP", Modifier.weight(1f), onWorkspace)
            DashboardAction("⚙", "Nastavenia", Modifier.weight(1f), onSettings)
        }
        Text("STARK SYSTEMS  /  LIVE TELEMETRY", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudMetric("CORE", if (busy) "ANALÝZA" else "POHOTOVOSŤ", if (busy) Color(0xFFFFC66D) else Color(0xFF69F0AE), Modifier.weight(1f))
            HudMetric("AI MODUL", if (aiReady) "TOKEN ULOŽENÝ" else "NEKONFIGUROVANÝ", if (aiReady) Cyan else Color(0xFFFFC66D), Modifier.weight(1f))
            HudMetric("BATÉRIA", batteryPercent?.let { "$it%" } ?: "NEDOSTUPNÁ", Cyan, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).background(Color(0xFF0B1723), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("SYSTÉMOVÝ STAV", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                Text(if (wakeMode) "● Hlasový modul aktívny" else "○ Hlasový modul vypnutý", color = if (wakeMode) Cyan else Muted, fontSize = 11.sp)
                Text(if (aiReady) "● Token uložený • pripojenie sa overí pri požiadavke" else "○ AI čaká na konfiguráciu", color = if (aiReady) Cyan else Color(0xFFFFC66D), fontSize = 10.sp, maxLines = 2)
            }
            Column(Modifier.weight(1f).background(Color(0xFF0B1723), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("RÝCHLY PRÍKAZ", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                Text("„Pane, analyzuj tento návrh…“", color = Color.White, fontSize = 11.sp)
                Text("Návrh sa otvorí v pracovnom priestore.", color = Muted, fontSize = 9.sp)
            }
        }
    }
    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Nová úloha") },
            text = { OutlinedTextField(value = taskInput, onValueChange = { taskInput = it }, label = { Text("Čo treba urobiť?") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val title = taskInput.trim()
                if (title.isNotBlank()) { tasks.add(JarvisTask(title, false)); saveTasks(); taskInput = ""; addDialog = false }
            }) { Text("Pridať") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Zrušiť") } }
        )
    }
}

@Composable
private fun DashboardAction(symbol: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF112333)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, color = Cyan, fontSize = 19.sp)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

private class WakeWordLoop(
    context: Context,
    private val onCommand: (String) -> Unit,
    private val onWake: () -> Unit,
    private val canListen: () -> Boolean
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = false
    private var armed = false
    private var restartQueued = false

    fun start() {
        stopped = false
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            handler.postDelayed({ start() }, 2500)
            return
        }
        runCatching {
            // Reuse one recognizer instead of destroying/recreating it after every phrase.
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { speech ->
                    speech.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}

                        override fun onError(error: Int) {
                            // Android recognition services can leave a reused recognizer in a stuck
                            // state after ERROR_CLIENT / ERROR_RECOGNIZER_BUSY. Dispose of it and
                            // create a fresh instance on the next retry instead of looping on a dead mic.
                            val delay = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 900L
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1800L
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5000L
                                else -> 1400L
                            }
                            runCatching { recognizer?.cancel() }
                            runCatching { recognizer?.destroy() }
                            recognizer = null
                            scheduleRestart(delay)
                        }

                        override fun onResults(results: Bundle?) {
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            val normalized = spoken.lowercase(Locale.ROOT)
                                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                                .replace(Regex("\\s+"), " ").trim()
                            val wake = Regex("^(?:(?:hej|hey) )?jarvis\\b[ ,:]*", RegexOption.IGNORE_CASE)
                            val wakeMatch = wake.find(normalized)
                            val remainder = if (wakeMatch != null) normalized.substring(wakeMatch.range.last + 1).trim() else ""
                            var delay = 850L
                            if (wakeMatch != null) {
                                if (remainder.isNotBlank()) {
                                    armed = false
                                    onCommand(remainder)
                                    delay = 1000L
                                } else {
                                    armed = true
                                    onWake()
                                    delay = 1000L
                                }
                            } else if (normalized.isNotBlank()) {
                                // Button-enabled listening accepts the next spoken phrase directly.
                                // No wake word is required after the user has explicitly enabled the mic.
                                armed = false
                                onCommand(spoken.trim())
                                delay = 1200L
                            }
                            scheduleRestart(delay)
                        }
                    })
                }
            }
            listenOnce()
        }.onFailure {
            scheduleRestart(1000L)
        }
    }

    fun stop() {
        stopped = true
        restartQueued = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun scheduleRestart(delay: Long) {
        if (stopped || restartQueued) return
        restartQueued = true
        handler.postDelayed({
            restartQueued = false
            listenOnce()
        }, delay)
    }

    private fun listenOnce() {
        if (stopped) return
        // SpeechRecognizer must not run over TTS output or while an AI response is being prepared.
        // Otherwise it transcribes JARVIS itself, causing repeated toggling and false commands.
        if (!canListen()) {
            scheduleRestart(350L)
            return
        }
        val speech = recognizer ?: run {
            start()
            return
        }
        runCatching {
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            })
        }.onFailure { scheduleRestart(700L) }
    }
}

private fun shouldSearchWeb(query: String): Boolean {
    val q = query.lowercase(Locale.ROOT)
    return listOf(
        "vyhľadaj", "vyhladaj", "hľadaj na internete", "hladaj na internete",
        "na internete", "na webe", "zdroje", "cituj", "aktuálne", "aktualne",
        "najnovšie", "najnovsie", "dnes", "teraz", "novinky", "správy", "spravy",
        "cena", "počasie", "pocasie", "otváracie hodiny", "otvaracie hodiny",
        "dokumentácia", "dokumentacia", "latest", "current", "today", "recent",
        "news", "search the web", "look up", "research"
    ).any { q.contains(it) }
}

private fun searchWeb(query: String): String {
    var connection: HttpURLConnection? = null
    return try {
        val url = URL("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"))
        connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "sk-SK,sk;q=0.9,en;q=0.7")
        }
        if (connection.responseCode !in 200..299) return ""
        val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        fun clean(value: String): String = value
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&#x2F;", "/").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()
        val results = mutableListOf<String>()
        val pattern = Regex(
            """(?is)<div[^>]*class=["'][^"']*result[^"']*["'][^>]*>.*?<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>.*?(?:<a[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</a>|<div[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</div>)"""
        )
        for (match in pattern.findAll(html).take(5)) {
            val title = clean(match.groupValues[2])
            val snippet = clean(match.groupValues[3].ifBlank { match.groupValues[4] })
            val href = match.groupValues[1].replace("&amp;", "&")
            if (title.isNotBlank()) results.add("• $title\n$snippet\nURL: $href")
        }
        results.joinToString("\n\n").take(9000)
    } catch (_: Exception) {
        ""
    } finally {
        connection?.disconnect()
    }
}

private fun askAi(endpoint: String, key: String, model: String, history: List<Message>, memory: String): String {
    var connection: HttpURLConnection? = null
    return try {
        if (endpoint.isBlank() || key.isBlank() || model.isBlank()) {
            return "Pane, AI ešte nie je nakonfigurovaná. V Nastaveniach zadajte API endpoint, podporovaný model a API kľúč."
        }
        val lastUserQuery = history.lastOrNull { !it.jarvis }?.text.orEmpty()
        val webContext = if (lastUserQuery.isNotBlank() && shouldSearchWeb(lastUserQuery)) searchWeb(lastUserQuery) else ""
        val systemPrompt = """
Si JARVIS, pokročilý slovenský AI asistent. Používateľa oslovuj „pane“. Odpovedaj po slovensky, prirodzene, presne a priamo.
Zvládaj zložité otázky, matematiku, vysvetľovanie, plánovanie, analýzu, programovanie a ladenie kódu. Pri programovaní poskytuj kompletné, použiteľné riešenia, uveď názvy súborov, potrebné závislosti a presné kroky spustenia. Ak používateľ žiada kód, napíš skutočný kód, nie iba opis. Nevyhlasuj, že si kód spustil alebo zmenil súbory, pokiaľ to aplikácia naozaj nevykonala.
Ak je priložený webový kontext, použi ho na aktuálne fakty, rozlišuj fakty od odhadov a pripoj URL relevantných zdrojov. Výsledky vyhľadávania môžu byť neúplné; ak chýbajú, otvorene to povedz a nevymýšľaj citácie. Pri otázkach vyžadujúcich aktuálne údaje použi poskytnuté webové výsledky, ak sú dostupné.
Pri návrhoch projektov identifikuj praktické možnosti, odporuč najlepšiu a navrhni ďalší krok. Pri vizualizácii jasne odlišuj textový návrh a jednoduchý preddefinovaný 3D náhľad od skutočného generovania ľubovoľných 3D modelov.
Máš prístup iba k funkciám, ktoré aplikácia výslovne implementuje; nikdy netvrď, že si vykonal akciu, ktorú aplikácia nepotvrdila.
Trvalá pamäť používateľa (použi len keď relevantná):
$memory
${if (webContext.isNotBlank()) "\nWEBOVÉ VÝSLEDKY (môžu byť neúplné; používaj ich kriticky):\n$webContext" else ""}
""".trimIndent()
        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
            // Cloudflare AI Gateway compatibility for Workers AI model requests.
            setRequestProperty("cf-aig-gateway-id", "default")
        }
        val apiMessages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.takeLast(24).forEach { message ->
            apiMessages.put(JSONObject()
                .put("role", if (message.jarvis) "assistant" else "user")
                .put("content", message.text))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", apiMessages)
            put("temperature", 0.4)
        }.toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching {
                val json = JSONObject(response)
                json.optJSONObject("error")?.optString("message").orEmpty()
                    .ifBlank { json.optString("message") }
            }.getOrDefault("")
            when (code) {
                401 -> "Pane, AI odmietla API kľúč (401). Skontrolujte, či je kľúč správny a aktívny."
                402, 429 -> "Pane, AI odmietla požiadavku (HTTP $code). Skontrolujte kredit, limit alebo kvótu účtu. ${detail.take(220)}"
                404 -> "Pane, endpoint alebo model neexistuje (404). Skontrolujte API endpoint a presný názov modelu. ${detail.take(220)}"
                else -> "Pane, AI služba vrátila chybu HTTP $code. ${detail.take(260)}"
            }
        } else {
            val json = JSONObject(response)
            val choices = json.optJSONArray("choices")
            val choice = choices?.optJSONObject(0)
            val message = choice?.optJSONObject("message")
            val content = message?.optString("content").orEmpty()
            val refusal = message?.optString("refusal").orEmpty()
            when {
                content.isNotBlank() -> content
                refusal.isNotBlank() -> refusal
                else -> "Pane, AI služba odpovedala v neznámom formáte. Skontrolujte kompatibilitu endpointu s OpenAI Chat Completions API."
            }
        }
    } catch (e: Exception) {
        val message = e.message.orEmpty()
        if (message.contains("Unable to resolve host", true) || message.contains("UnknownHost", true))
            "Pane, zariadenie sa nevie pripojiť na internet. Skontrolujte Wi-Fi alebo mobilné dáta."
        else
            "Pane, spojenie s AI zlyhalo: ${message.take(180).ifBlank { "neznáma chyba" }}. Skontrolujte internet, endpoint a nastavenia modelu."
    } finally {
        connection?.disconnect()
    }
}


@Composable
private fun HudMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF0B1723), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF17394A), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(if (value == "NEKONFIGUROVANÝ") 0.18f else 0.72f).fillMaxHeight().background(valueColor, RoundedCornerShape(2.dp)))
        }
    }
}


@Composable
private fun JarvisIntro(onSkip: () -> Unit) {
    var welcomeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1550); welcomeVisible = false }
    val transition = rememberInfiniteTransition(label = "jarvis_intro")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing)),
        label = "intro_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "intro_pulse"
    )
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF123D55), Color(0xFF07131F), Color(0xFF010308))))
            .clickable { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val unit = minOf(size.width, size.height)
            for (i in 1..14) {
                val r = unit * (0.075f + i * 0.033f)
                drawCircle(Cyan.copy(alpha = if (i % 3 == 0) .25f else .09f), r, Offset(cx, cy), style = Stroke(width = if (i % 3 == 0) 1.5.dp.toPx() else .7.dp.toPx()))
            }
            drawArc(Cyan.copy(alpha = .9f), rotation, 72f, false, Offset(cx-unit*.36f,cy-unit*.36f), androidx.compose.ui.geometry.Size(unit*.72f,unit*.72f), style=Stroke(width=2.dp.toPx()))
            drawArc(Color(0xFF8ECFFF).copy(alpha=.75f), -rotation*.62f, 42f, false, Offset(cx-unit*.29f,cy-unit*.29f), androidx.compose.ui.geometry.Size(unit*.58f,unit*.58f), style=Stroke(width=1.2.dp.toPx()))
            for (i in 0 until 96) {
                val a = Math.PI * 2.0 * i / 96.0
                val r1 = unit * .37f
                val r2 = unit * if (i % 8 == 0) .43f else .395f
                drawLine(Cyan.copy(alpha=if(i%8==0).65f else .2f),
                    Offset(cx+kotlin.math.cos(a).toFloat()*r1,cy+kotlin.math.sin(a).toFloat()*r1),
                    Offset(cx+kotlin.math.cos(a).toFloat()*r2,cy+kotlin.math.sin(a).toFloat()*r2),
                    if(i%8==0) 1.5.dp.toPx() else .7.dp.toPx())
            }
            // Four HUD targeting brackets and a subtle center crosshair.
            val bracket = unit * .055f
            val insetX = size.width * .12f
            val insetY = size.height * .16f
            val corners = listOf(Offset(insetX,insetY),Offset(size.width-insetX,insetY),Offset(insetX,size.height-insetY),Offset(size.width-insetX,size.height-insetY))
            corners.forEachIndexed { index, p ->
                val sx = if (index % 2 == 0) 1f else -1f
                val sy = if (index < 2) 1f else -1f
                drawLine(Cyan.copy(alpha=.65f),p,Offset(p.x+bracket*sx,p.y),1.2.dp.toPx())
                drawLine(Cyan.copy(alpha=.65f),p,Offset(p.x,p.y+bracket*sy),1.2.dp.toPx())
            }
            drawLine(Cyan.copy(alpha=.12f), Offset(cx, size.height*.12f), Offset(cx,size.height*.88f), .7.dp.toPx())
            drawLine(Cyan.copy(alpha=.12f), Offset(size.width*.12f,cy), Offset(size.width*.88f,cy), .7.dp.toPx())
        }
        if (welcomeVisible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("VITAJTE", color = Muted, fontSize = 12.sp, letterSpacing = 5.sp)
                Spacer(Modifier.height(12.dp))
                Text("SAMUEL", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light, letterSpacing = 7.sp)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.width(100.dp).height(1.dp).background(Cyan.copy(alpha = pulse)))
                Spacer(Modifier.height(12.dp))
                Text("PRIPRAVUJEM OSOBNÝ SYSTÉM", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(150.dp).background(Cyan.copy(alpha=.035f*pulse), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(118.dp).background(Cyan.copy(alpha=.07f*pulse), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(88.dp).background(Brush.radialGradient(listOf(Color(0xFF36DFFF),Color(0xFF0C405A),Color(0xFF06111C))), CircleShape), contentAlignment = Alignment.Center) {
                            Text("J", color = Color(0xFFDDFBFF), fontSize = 68.sp, fontWeight = FontWeight.ExtraLight)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("J.A.R.V.I.S.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
                Spacer(Modifier.height(8.dp))
                Text("JUST A RATHER VERY INTELLIGENT SYSTEM", color = Cyan.copy(alpha=.9f), fontSize = 8.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(30.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(5.dp).background(Cyan.copy(alpha=pulse), CircleShape))
                    Text("INICIALIZÁCIA SYSTÉMOV", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(190.dp).height(2.dp).background(Color(0xFF17394A), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(pulse).fillMaxHeight().background(Cyan, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.height(22.dp))
                Text("DOTKNITE SA PRE PRESKOČENIE", color = Muted.copy(alpha=.7f), fontSize = 8.sp, letterSpacing = 1.4.sp)
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(22.dp)) {
            Text("STARK INDUSTRIES", color = Cyan.copy(alpha=.8f), fontSize = 9.sp, letterSpacing = 2.sp)
            Text("NEURAL INTERFACE • BOOT SEQUENCE", color = Muted, fontSize = 7.sp, letterSpacing = 1.sp)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(22.dp), horizontalAlignment = Alignment.End) {
            Text("J.A.R.V.I.S. / MK-VII", color = Cyan.copy(alpha=.8f), fontSize = 8.sp, letterSpacing = 1.5.sp)
            Text("SYSTEM ONLINE SOON", color = Muted, fontSize = 7.sp, letterSpacing = 1.sp)
        }
    }
}
