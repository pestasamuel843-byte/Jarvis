package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.view.KeyEvent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.border
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
                override fun onDone(utteranceId: String?) { speechOutputActive = false; if (JarvisVoiceService.isRunning) startService(Intent(this@MainActivity, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_RESUME)) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speechOutputActive = false; startService(Intent(this@MainActivity, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_RESUME)) }
                override fun onError(utteranceId: String?, errorCode: Int) { speechOutputActive = false; startService(Intent(this@MainActivity, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_RESUME)) }
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
        if (JarvisVoiceService.isRunning) startService(Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_PAUSE))
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
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions")?.let { if (it.contains("api.cloudflare.com/client/v4/accounts/09f7e11cc7eaf76d61294654d547bd8a") || it == "https://api.openai.com/v1/chat/completions") "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" else it } ?: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions") }
    var model by remember { mutableStateOf(prefs.getString("model", "gemini-2.5-flash")?.let { if (it == "@cf/meta/llama-3.1-8b-instruct" && endpoint.contains("generativelanguage.googleapis.com")) "gemini-2.5-flash" else it } ?: "gemini-2.5-flash") }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var settings by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var apiTestResult by remember { mutableStateOf("") }
    var apiTesting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var wakeMode by remember { mutableStateOf(prefs.getBoolean("voice_enabled", false)) }
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
    var scheduleSetupIndex by remember { mutableIntStateOf(-1) }
    val scheduleDays = listOf("Pondelok", "Utorok", "Streda", "Štvrtok", "Piatok")
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
    val documentScanner = remember {
        val options = GmsDocumentScanningOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScanningOptions.RESULT_FORMAT_JPEG, GmsDocumentScanningOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScanningOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val documentScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scan?.pages?.size ?: 0
            val pdf = scan?.pdf?.uri
            val message = if (pages > 0) "Dokument bol naskenovaný. Počet strán: $pages." +
                (if (pdf != null) " PDF je pripravené na otvorenie." else "")
                else "Skenovanie sa dokončilo, ale výsledok sa nepodarilo načítať."
            chat.add(Message(true, message)); speak(message)
            if (pdf != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(pdf, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        } else {
            val message = "Skenovanie bolo zrušené."
            chat.add(Message(true, message)); speak(message)
        }
    }
    fun startDocumentScan() {
        documentScanner.getStartScanIntent(context as Activity)
            .addOnSuccessListener { sender -> documentScanLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                val message = "Skener sa nepodarilo spustiť. Skontrolujte Google Play služby a internet."
                chat.add(Message(true, message)); speak(message)
            }
    }
    fun handle(raw: String) {
        val text = raw.trim().replace(Regex("(?i)^(?:(?:hej|hey)\\s+)?jarvis[,: ]*"), "").trim()
        if (text.isEmpty() || busy) return
        if (scheduleSetupIndex >= 0) {
            val day = scheduleDays[scheduleSetupIndex / 8]
            val period = scheduleSetupIndex % 8 + 1
            val saved = prefs.getString("school_timetable", "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
            if (!text.equals("voľno", true) && !text.equals("preskoč", true) && !text.equals("ďalej", true)) saved.add("$day | $period. hodina | $text")
            prefs.edit().putString("school_timetable", saved.joinToString("\\n")).apply()
            scheduleSetupIndex++
            if (scheduleSetupIndex >= 40) {
                scheduleSetupIndex = -1
                val done = "Rozvrh som uložil, pane. Ak chcete zmeniť konkrétnu hodinu, povedzte: uprav rozvrh."
                chat.add(Message(true, done)); speak(done)
            } else {
                val nextDay = scheduleDays[scheduleSetupIndex / 8]
                val nextPeriod = scheduleSetupIndex % 8 + 1
                val question = "$nextDay, $nextPeriod. hodina. Aký predmet máte? Povedzte predmet, alebo voľno."
                chat.add(Message(true, question)); speak(question)
            }
            showChat = true
            return
        }
        showChat = true
        input = ""
        chat.add(Message(false, text))
        when {
            text.contains("nastav rozvrh", true) || text.contains("pridaj rozvrh", true) || text == "rozvrh" || text.contains("rozvrh školy", true) -> {
                scheduleSetupIndex = 0
                val old = "school_timetable"
                prefs.edit().remove(old).apply()
                val question = "Začnime rozvrhom, pane. Budem sa pýtať na každý predmet zvlášť a po každej odpovedi si ho uložím. Pondelok, prvá hodina: aký predmet máte? Ak hodina nie je, povedzte voľno."
                chat.add(Message(true, question)); speak(question)
            }
            text.contains("ukáž rozvrh", true) || text.contains("môj rozvrh", true) -> {
                val timetable = prefs.getString("school_timetable", "").orEmpty()
                val answer = if (timetable.isBlank()) "Pane, rozvrh ešte nie je nastavený. Povedzte: nastav rozvrh." else "Váš uložený rozvrh: " + timetable.replace("\\n", ". ")
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("graf", true) || text.contains("známk", true) || text.contains("zlepšil", true) || text.contains("zhoršil", true) || text.contains("mentáln", true) || text.contains("ako som sa mal", true) || text.contains("zdrav", true) || text.contains("osobný prehľad", true) -> {
                showProgress = true
                val progress = prefs.getString("progress_data", "").orEmpty()
                val gradeCount = progress.lines().count { it.contains(" | ZNÁMKA | ") }
                val dayCount = progress.lines().count { it.contains(" | DEŇ | ") }
                val answer = when {
                    progress.isBlank() -> "Pane, osobný prehľad je otvorený. Zatiaľ nemám uložené známky ani denné hodnotenia. Počas denného prehľadu ich môžete pridať. Údaje zostávajú v tomto zariadení."
                    else -> "Pane, zobrazil som osobné grafy. V zariadení je uložených $gradeCount záznamov známok a $dayCount denných hodnotení. Grafy zobrazujú iba údaje, ktoré ste sami zadali; nie sú zdravotnou diagnostikou."
                }
                chat.add(Message(true, answer)); speak(answer)
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
            text.contains("nastavenia wi-fi", true) || text.contains("nastavenia wifi", true) || text.contains("zapni wi-fi", true) || text.contains("zapni wifi", true) || text.contains("vypni wi-fi", true) || text.contains("vypni wifi", true) -> {
                val answer = "Otváram systémové nastavenia Wi-Fi, pane. Zmenu potvrďte priamo v Androide."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia Wi-Fi sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("nastavenia bluetooth", true) || text.contains("zapni bluetooth", true) || text.contains("vypni bluetooth", true) -> {
                val answer = "Otváram nastavenia Bluetooth, pane. Zmenu potvrďte priamo v Androide."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia Bluetooth sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("nastavenia zvuku", true) || text.contains("hlasitosť", true) || text.contains("nastavenia hlasitosti", true) -> {
                val answer = "Otváram nastavenia zvuku, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia zvuku sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("nastavenia displeja", true) || text.contains("jas obrazovky", true) || text.contains("nastavenia obrazovky", true) -> {
                val answer = "Otváram nastavenia displeja, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia displeja sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("nastavenia aplikácií", true) || text.contains("povolenia aplikácií", true) || text.contains("oprávnenia", true) -> {
                val answer = "Otváram systémové nastavenia aplikácií a oprávnení, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Nastavenia aplikácií sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor súbory", true) || text.contains("otvor správcu súborov", true) || text.contains("moje súbory", true) -> {
                val answer = "Otváram správcu súborov, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }) }
                    .onFailure { Toast.makeText(context, "Výber súborov sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor instagram", true) || text.contains("spusti instagram", true) -> {
                val answer = "Otváram Instagram, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))) }
                    .onFailure { Toast.makeText(context, "Instagram sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("otvor galériu", true) || text.contains("otvor fotografie", true) || text.contains("moje fotky", true) -> {
                val answer = "Otváram galériu fotografií, pane."
                chat.add(Message(true, answer)); speak(answer)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setType("image/*")) }
                    .onFailure { Toast.makeText(context, "Galériu sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
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
            text.contains("vypni počúvanie", true) || text.contains("prestaň počúvať", true) || text.contains("vypni mikrofón", true) || text.contains("jarvis spi", true) || text.contains("ukonči rozhovor", true) || text.contains("ukonči konverzáciu", true) -> {
                wakeMode = false
                listening = false
                prefs.edit().putBoolean("voice_enabled", false).apply()
                context.stopService(Intent(context, JarvisVoiceService::class.java))
                val answer = "Hlasové počúvanie vypnuté, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("zapni počúvanie", true) || text.contains("zapni mikrofón", true) -> {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    wakeMode = true
                    listening = true
                    prefs.edit().putBoolean("voice_enabled", true).apply()
                    context.startForegroundService(Intent(context, JarvisVoiceService::class.java))
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
                    val answer = "Povedzte cieľ, napríklad: naviguj do Svitu, pane."
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
            text.contains("stopni film", true) || text.contains("pozastav film", true) || text.contains("pauzni film", true) || text == "pauza" || text == "pozastav" -> {
                val sent = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
                val answer = if (sent) "Odoslal som príkaz na pozastavenie prehrávania, pane. Výsledok závisí od prehrávača." else "Príkaz na pozastavenie sa nepodarilo odoslať, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("pokračuj vo filme", true) || text.contains("pokračuj v prehrávaní", true) || text.contains("spusti prehrávanie", true) || text == "pokračuj" || text == "prehraj" -> {
                val sent = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                val answer = if (sent) "Odoslal som príkaz na pokračovanie prehrávania, pane. Výsledok závisí od prehrávača." else "Príkaz na pokračovanie sa nepodarilo odoslať, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("ďalšia skladba", true) || text.contains("ďalšia epizóda", true) || text == "ďalej" || text == "preskoč" -> {
                val sent = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                val answer = if (sent) "Odoslal som príkaz na ďalšiu položku, pane." else "Príkaz sa nepodarilo odoslať, pane."
                chat.add(Message(true, answer)); speak(answer)
            }
            text.contains("predošlá skladba", true) || text.contains("predchádzajúca epizóda", true) || text == "predošlá" -> {
                val sent = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                val answer = if (sent) "Odoslal som príkaz na predchádzajúcu položku, pane." else "Príkaz sa nepodarilo odoslať, pane."
                chat.add(Message(true, answer)); speak(answer)
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
            text.contains("skenuj dokument", true) || text.contains("naskenuj dokument", true) || text.contains("spusti skener", true) || text.contains("skenuj papier", true) -> {
                val message = "Spúšťam skener dokumentov. Zarovnajte strany v kamere a potvrďte skenovanie."
                chat.add(Message(true, message)); speak(message)
                startDocumentScan()
            }
            text.contains("otvor gmail", true) || text.contains("spusti gmail", true) || text.contains("otvor email", true) || text.contains("otvor e mail", true) -> {
                val message = "Otváram Gmail, pane."
                chat.add(Message(true, message)); speak(message)
                val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                runCatching {
                    if (launch != null) context.startActivity(launch)
                    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/")))
                }.onFailure { Toast.makeText(context, "Gmail sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show() }
            }
            text.contains("prečítaj správy", true) || text.contains("čítaj správy", true) || text.contains("prečítaj emaily", true) || text.contains("prečítaj e-maily", true) || text.contains("nové správy", true) || text.contains("správy z messenger", true) || text.contains("správy z instagram", true) || text.contains("správy z facebook", true) -> {
                val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                    .contains(context.packageName + "/" + GmailNotificationListener::class.java.name)
                if (!enabled) {
                    val message = "Na čítanie náhľadov správ z Gmailu, Messengeru, Instagramu a Facebooku povoľte JARVISu prístup k oznámeniam Androidu. Otváram systémové nastavenie. Potom povedzte: Hej Jarvis, prečítaj správy."
                    chat.add(Message(true, message)); speak(message)
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                } else {
                    val previews = runCatching { JSONArray(context.getSharedPreferences("jarvis_gmail", Context.MODE_PRIVATE).getString("previews", "[]")) }.getOrDefault(JSONArray())
                    val filterApp = when {
                        text.contains("messenger", true) -> "Messenger"
                        text.contains("instagram", true) -> "Instagram"
                        text.contains("facebook", true) -> "Facebook"
                        text.contains("gmail", true) || text.contains("email", true) || text.contains("e-mail", true) -> "Gmail"
                        else -> ""
                    }
                    val messages = (0 until previews.length()).mapNotNull { i ->
                        val item = previews.optJSONObject(i) ?: return@mapNotNull null
                        val app = item.optString("app", "Gmail")
                        if (filterApp.isNotBlank() && app != filterApp) return@mapNotNull null
                        val title = item.optString("title").trim()
                        val body = item.optString("text").trim()
                        if (title.isBlank() && body.isBlank()) null else {
                            val sender = title.ifBlank { "Neznámy odosielateľ" }
                            val content = body.ifBlank { "V oznámení nie je zobrazený text správy." }
                            "$app|$sender|$content"
                        }
                    }
                    val banter = listOf(
                        "Pane, vyzerá to, že dnes máte celkom rušný inbox.",
                        "Tak, pane, poďme zistiť, kto sa vám ozval.",
                        "Pane, správy sa vám dnes veru nehromadia samy."
                    ).random()
                    val answer = if (messages.isEmpty()) {
                        if (filterApp.isBlank()) "Pane, zatiaľ tu nemám nové náhľady správ. Buď je pokoj, alebo si všetci dávajú načas."
                        else "Pane, v oznámeniach zatiaľ nemám náhľady z aplikácie $filterApp. Možno je tam dnes nezvyčajné ticho."
                    } else {
                        val spokenMessages = messages.take(8).mapIndexed { index, entry ->
                            val parts = entry.split("|", limit = 3)
                            val app = parts.getOrElse(0) { "Aplikácia" }
                            val sender = parts.getOrElse(1) { "Neznámy odosielateľ" }
                            val body = parts.getOrElse(2) { "" }
                            val lead = when (index) {
                                0 -> "Ozval sa vám niekto cez $app. Odosielateľ: $sender."
                                1 -> "A máme tu ďalšiu správu. $sender píše cez $app."
                                else -> "Ďalej sa ozval $sender."
                            }
                            "$lead $body"
                        }
                        banter + " " + spokenMessages.joinToString(" Ďalšia správa. ")
                    }
                    chat.add(Message(true, answer)); speak(answer)
                }
            }
            text.contains("počasie", true) || text.contains("predpoveď", true) || text.contains("teplota vonku", true) -> {
                val answer = "Zisťujem aktuálne počasie pre Svit z online meteorologických dát, pane."
                chat.add(Message(true, answer)); speak(answer)
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { LiveWeather.forSvit() }.getOrElse {
                            "Pane, živé údaje o počasí sa nepodarilo načítať. Skontrolujte internetové pripojenie a skúste to znova. Dátum a čas merania preto nebudem odhadovať."
                        }
                    }
                    chat.add(Message(true, result))
                    speak(if (result.startsWith("Pane, živé")) "Počasie sa momentálne nepodarilo načítať." else "Aktuálne počasie pre Svit je pripravené.")
                    busy = false
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
                busy = true
                scope.launch {
                    val brain = OfflineBrain(context)
                    val answer = if (brain.isInstalled()) {
                        runCatching { brain.answer(text) }.getOrElse { "Pane, offline AI model sa nepodarilo spustiť: ${it.message ?: "neznáma chyba"}" }
                    } else {
                        "Pane, lokálne príkazy fungujú. Ak chcete aj konverzačnú AI bez API kľúča, otvorte Nastavenia a stiahnite Offline AI mozog (približne 330 MB)."
                    }
                    chat.add(Message(true, answer)); speak(answer); busy = false
                }
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
            prefs.edit().putBoolean("voice_enabled", true).apply()
            context.startForegroundService(Intent(context, JarvisVoiceService::class.java))
        } else {
            wakeMode = false
            listening = false
            Toast.makeText(context, "Bez povolenia mikrofónu hlasové ovládanie nefunguje.", Toast.LENGTH_LONG).show()
        }
    }

    // The voice service is user-enabled once and remains independent of Compose navigation.
    LaunchedEffect(Unit) {
        if (wakeMode && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            runCatching { context.startForegroundService(Intent(context, JarvisVoiceService::class.java)) }
                .onFailure { listening = false; Toast.makeText(context, "Hlasovú službu sa nepodarilo spustiť: ${it.message}", Toast.LENGTH_LONG).show() }
        } else {
            listening = false
        }
    }

    val latestVoiceCommand by rememberUpdatedState<(String) -> Unit>({ spoken -> handle(spoken) })
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    JarvisVoiceService.ACTION_COMMAND -> intent.getStringExtra(JarvisVoiceService.EXTRA_COMMAND)?.let { latestVoiceCommand(it) }
                    JarvisVoiceService.ACTION_WAKE -> speak("Počúvam, pane.")
                    JarvisVoiceService.ACTION_STATUS -> {
                        val status = intent.getStringExtra(JarvisVoiceService.EXTRA_STATUS).orEmpty()
                        listening = status != "HLASOVÝ MODUL VYPNUTÝ"
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(JarvisVoiceService.ACTION_COMMAND)
            addAction(JarvisVoiceService.ACTION_WAKE)
            addAction(JarvisVoiceService.ACTION_STATUS)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10263A), Bg, Color(0xFF05070C))))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(52.dp).background(Brush.radialGradient(listOf(Color(0xFF1B718B), Color(0xFF0B2638), Color(0xFF07111B))), CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(Cyan.copy(alpha = .8f), size.minDimension * .39f, c, style = Stroke(width = 1.2.dp.toPx()))
                            drawArc(Cyan, 35f, 105f, false, androidx.compose.ui.geometry.Offset(size.width*.12f,size.height*.12f), androidx.compose.ui.geometry.Size(size.width*.76f,size.height*.76f), style = Stroke(width = 1.4.dp.toPx()))
                            drawLine(Cyan.copy(alpha=.7f), Offset(size.width*.22f,size.height*.5f), Offset(size.width*.78f,size.height*.5f), .8.dp.toPx())
                            drawLine(Cyan.copy(alpha=.7f), Offset(size.width*.5f,size.height*.22f), Offset(size.width*.5f,size.height*.78f), .8.dp.toPx())
                        }
                        Text("J", color = Color(0xFFE2FCFF), fontSize = 25.sp, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                        Text(if (busy) "◉ NEURÁLNA ANALÝZA…" else if (wakeMode) "◉ HLASOVÝ MODUL AKTÍVNY" else "NEURÁLNE ROZHRANIE • SK-SK", color = Cyan, fontSize = 9.sp, letterSpacing = 1.2.sp)
                        Text("STARK SYSTEMS  /  MK-VII", color = Muted, fontSize = 7.sp, letterSpacing = 1.6.sp)
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
                            if (wakeMode) {
                                wakeMode = false
                                listening = false
                                prefs.edit().putBoolean("voice_enabled", false).apply()
                                context.stopService(Intent(context, JarvisVoiceService::class.java))
                            } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                wakeMode = true
                                listening = true
                                prefs.edit().putBoolean("voice_enabled", true).apply()
                                context.startForegroundService(Intent(context, JarvisVoiceService::class.java))
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
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
                Text(if (wakeMode) "HLASOVÁ RELÁCIA AKTÍVNA • ďalšie otázky bez opakovania Hey Jarvis • ukončite: Jarvis, spi." else "HLASOVÁ RELÁCIA VYPNUTÁ • stlačte 🎤 a povedzte Hey Jarvis.", color = Muted, fontSize = 10.sp)
                }
                // Persistent navigation makes every primary screen reachable in one tap.
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF24465A), RoundedCornerShape(16.dp))
                        .background(Color(0xCC091522), RoundedCornerShape(16.dp)).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val navItems = listOf(
                        Triple("⌂", "DOMOV", "home"),
                        Triple("◉", "CHAT", "chat"),
                        Triple("◇", "PROJEKT", "project"),
                        Triple("⚙", "NASTAV.", "settings")
                    )
                    navItems.forEach { item ->
                        val selected = when (item.third) {
                            "home" -> !showChat && !showWorkspace
                            "chat" -> showChat && !showWorkspace
                            "project" -> showWorkspace
                            else -> false
                        }
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                                .background(if (selected) Color(0xFF12364A) else Color.Transparent)
                                .clickable {
                                    when (item.third) {
                                        "home" -> { showWorkspace = false; showChat = false }
                                        "chat" -> { showWorkspace = false; showChat = true }
                                        "project" -> { showChat = false; showWorkspace = true }
                                        "settings" -> settings = true
                                    }
                                }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.first, color = if (selected) Cyan else Muted, fontSize = 18.sp)
                            Text(item.second, color = if (selected) Color.White else Muted, fontSize = 8.sp, letterSpacing = .7.sp)
                        }
                    }
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
                        Text("OFFLINE AI MOZOG", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Stiahne model Qwen3 INT4 (približne 330 MB). Potom bude lokálna AI fungovať bez API kľúča a bez internetu.", color = Muted, fontSize = 11.sp)
                        Button(
                            onClick = {
                                apiTesting = true
                                apiTestResult = "Sťahujem offline AI model…"
                                scope.launch {
                                    try {
                                        OfflineBrain(context).download { percent ->
                                            apiTestResult = "Sťahujem offline AI model: $percent %"
                                        }
                                        apiTestResult = "Offline AI model je stiahnutý."
                                    } catch (error: Exception) {
                                        apiTestResult = "Stiahnutie modelu zlyhalo: ${error.message ?: "neznáma chyba"}"
                                    } finally {
                                        apiTesting = false
                                    }
                                }
                            },
                            enabled = !apiTesting
                        ) { Text(if (apiTesting) "PRACUJEM…" else "STIAHNUŤ OFFLINE AI MOZOG") }
                        Text("JARVIS môže používať lokálnu AI bez kľúča alebo voliteľnú online AI službu. Offline model sa stiahne iba raz a zostane uložený v zariadení.", color = Muted, fontSize = 12.sp)
                        Text("Predvolená služba: Google Gemini API • model Gemini 2.5 Flash", color = Cyan, fontSize = 12.sp)
                        OutlinedTextField(key, { key = it }, label = { Text("Google Gemini API kľúč") }, placeholder = { Text("Vložte svoj kľúč z Google AI Studio") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }
                        }) { Text("ZÍSKAŤ API KĽÚČ V GOOGLE AI STUDIO", color = Cyan) }
                        Button(
                            onClick = {
                                apiTesting = true
                                apiTestResult = "Kontrolujem internetové pripojenie…"
                                scope.launch {
                                    val online = withContext(Dispatchers.IO) { testInternetConnection() }
                                    apiTestResult = if (online) "Internet funguje. Teraz môžete otestovať AI." else "Internet nie je dostupný. Skontrolujte Wi-Fi alebo mobilné dáta."
                                    apiTesting = false
                                }
                            },
                            enabled = !apiTesting
                        ) { Text(if (apiTesting) "KONTROLUJEM…" else "OTESTOVAŤ INTERNET") }
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
 modifier: Modifier, wakeMode: Boolean, busy: Boolean, aiReady: Boolean, model: String,
 onToggleVoice: () -> Unit, onWorkspace: () -> Unit, onChat: () -> Unit, onSettings: () -> Unit
) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "cinematic_hud")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "orbit")
    val counterSpin by transition.animateFloat(360f, 0f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "counter_orbit")
    val pulse by transition.animateFloat(.32f, 1f, infiniteRepeatable(tween(1450), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse), label = "core_pulse")
    val battery = remember { (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0,100) }
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF102C42), Color(0xFF06111D), Color(0xFF02050B))))) {
        val wide = maxWidth >= 600.dp
        // World-map hologram is drawn directly over the background, not boxed in a card.
        Canvas(Modifier.fillMaxSize()) {
            val ink = Cyan
            val top = size.height * .70f
            val h = size.height * .27f
            val continents = listOf(
                listOf(.05f to .18f,.09f to .06f,.17f to .08f,.22f to .21f,.19f to .34f,.22f to .48f,.17f to .67f,.13f to .49f,.10f to .37f,.06f to .27f),
                listOf(.23f to .07f,.31f to .06f,.37f to .18f,.35f to .32f,.30f to .40f,.28f to .60f,.24f to .46f,.22f to .28f),
                listOf(.42f to .13f,.47f to .06f,.57f to .09f,.62f to .18f,.71f to .13f,.79f to .20f,.85f to .14f,.93f to .24f,.88f to .35f,.79f to .39f,.73f to .30f,.66f to .43f,.60f to .34f,.55f to .42f,.50f to .30f,.45f to .32f),
                listOf(.48f to .35f,.56f to .37f,.59f to .51f,.56f to .68f,.52f to .83f,.49f to .65f,.46f to .49f),
                listOf(.77f to .47f,.84f to .45f,.89f to .52f,.86f to .61f,.81f to .58f)
            )
            continents.forEach { points ->
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = p.first * size.width
                    val y = top + p.second * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, ink.copy(alpha = .035f))
                drawPath(path, ink.copy(alpha = .22f), style = Stroke(.9.dp.toPx()))
            }
            for (i in 0..4) { val y = top + h * i / 4f; drawLine(ink.copy(alpha = .11f), Offset(0f,y), Offset(size.width,y), .6.dp.toPx()) }
            for (i in 0..12) { val x = size.width * i / 12f; drawLine(ink.copy(alpha = .06f), Offset(x,top), Offset(x,top+h), .5.dp.toPx()) }
            drawLine(ink.copy(alpha = .28f), Offset(0f,top+h*.56f), Offset(size.width,top+h*.56f), .7.dp.toPx())
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("STARK INDUSTRIES  /  PERSONAL AI", color=Cyan, fontSize=9.sp, letterSpacing=2.4.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("J.A.R.V.I.S.", color=Color.White, fontSize=30.sp, fontWeight=FontWeight.ExtraLight, letterSpacing=5.sp)
                        Text("NEURAL COMMAND DECK  •  MK-VII", color=Muted, fontSize=8.sp, letterSpacing=1.4.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        HudMetric("NEURAL LINK", if(aiReady) "ONLINE" else "AWAITING KEY", if(aiReady) Color(0xFF69F0AE) else Color(0xFFFFBD68), Modifier.fillMaxWidth())
                        HudMetric("VOICE CHANNEL", if(wakeMode) "LISTENING" else "STANDBY", if(wakeMode) Cyan else Muted, Modifier.fillMaxWidth())
                        HudMetric("POWER CELL", "${battery ?: "—"}%", Color.White, Modifier.fillMaxWidth())
                    }
                    Column {
                        Text("SYSTEM TIME", color=Muted, fontSize=8.sp, letterSpacing=1.8.sp)
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color=Color.White, fontSize=28.sp, fontWeight=FontWeight.ExtraLight)
                        Text(if(busy) "PROCESSING REQUEST…" else "ALL SYSTEMS NOMINAL", color=if(busy) Color(0xFFFFBD68) else Color(0xFF69F0AE), fontSize=8.sp, letterSpacing=1.sp)
                    }
                }
                Box(Modifier.weight(1.65f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFF0B2638), Color(0xFF06121E), Color(0xFF02060B))))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx=size.width*.5f; val cy=size.height*.48f; val unit=minOf(size.width,size.height); val r=unit*.265f
                        val grid=Color(0xFF24546A)
                        var x=0f; while(x<size.width){drawLine(grid.copy(alpha=.20f),Offset(x,0f),Offset(x,size.height),.55.dp.toPx());x+=28.dp.toPx()}
                        var y=0f; while(y<size.height){drawLine(grid.copy(alpha=.20f),Offset(0f,y),Offset(size.width,y),.55.dp.toPx());y+=28.dp.toPx()}
                        drawRect(Cyan.copy(alpha=.025f), size=androidx.compose.ui.geometry.Size(size.width,size.height))
                        for(i in 1..5) drawCircle(Cyan.copy(alpha=.035f),unit*(.12f+i*.095f),Offset(cx,cy),style=Stroke(if(i%2==0)1.1.dp.toPx() else .55.dp.toPx()))
                        drawCircle(Cyan.copy(alpha=.10f*pulse),r*1.72f,Offset(cx,cy))
                        drawArc(Cyan.copy(alpha=.95f),spin,104f,false,Offset(cx-r*1.48f,cy-r*1.48f),androidx.compose.ui.geometry.Size(r*2.96f,r*2.96f),style=Stroke(2.dp.toPx()))
                        drawArc(Color(0xFF8CDFFF).copy(alpha=.85f),counterSpin,64f,false,Offset(cx-r*1.28f,cy-r*1.28f),androidx.compose.ui.geometry.Size(r*2.56f,r*2.56f),style=Stroke(1.3.dp.toPx()))
                        drawArc(Cyan.copy(alpha=.75f),-spin*1.7f,36f,false,Offset(cx-r*1.92f,cy-r*1.92f),androidx.compose.ui.geometry.Size(r*3.84f,r*3.84f),style=Stroke(.8.dp.toPx()))
                        for(i in 0..95){val a=Math.PI*2*i/96;val r1=r*1.55f;val r2=r*(if(i%4==0)1.76f else 1.62f);drawLine(Cyan.copy(alpha=if(i%4==0).9f else .3f),Offset(cx+kotlin.math.cos(a).toFloat()*r1,cy+kotlin.math.sin(a).toFloat()*r1),Offset(cx+kotlin.math.cos(a).toFloat()*r2,cy+kotlin.math.sin(a).toFloat()*r2),if(i%4==0)1.2.dp.toPx() else .6.dp.toPx())}
                        drawLine(Cyan.copy(alpha=.16f),Offset(cx-r*2.2f,cy),Offset(cx+r*2.2f,cy),.7.dp.toPx())
                        drawLine(Cyan.copy(alpha=.16f),Offset(cx,cy-r*2.2f),Offset(cx,cy+r*2.2f),.7.dp.toPx())
                        for(i in 0..7){val a=Math.PI*2*i/8+spin*.008;val px=cx+kotlin.math.cos(a).toFloat()*r*2.08f;val py=cy+kotlin.math.sin(a).toFloat()*r*2.08f;drawCircle(Cyan.copy(alpha=.65f),2.2.dp.toPx(),Offset(px,py))}
                        val bx=size.width*.06f;val by=size.height*.08f;val l=18.dp.toPx()
                        listOf(Offset(bx,by),Offset(size.width-bx,by),Offset(bx,size.height-by),Offset(size.width-bx,size.height-by)).forEachIndexed { i,p -> val sx=if(i%2==0)1f else -1f;val sy=if(i<2)1f else -1f;drawLine(Cyan.copy(alpha=.7f),p,Offset(p.x+sx*l,p.y),1.4.dp.toPx());drawLine(Cyan.copy(alpha=.7f),p,Offset(p.x,p.y+sy*l),1.4.dp.toPx()) }
                    }
                    Column(Modifier.align(Alignment.TopStart).padding(18.dp)) { Text("HOLOGRAPHIC CORE  /  01",color=Cyan,fontSize=8.sp,letterSpacing=2.sp); Text(if(busy) "COGNITIVE PROCESS ACTIVE" else "NEURAL FIELD STABLE",color=Muted,fontSize=7.sp,letterSpacing=1.sp) }
                    Column(Modifier.align(Alignment.TopEnd).padding(18.dp),horizontalAlignment=Alignment.End) { Text("MK-VII  •  INTERFACE",color=Cyan,fontSize=8.sp,letterSpacing=1.5.sp); Text("SECURE LOCAL SESSION",color=Muted,fontSize=7.sp) }
                    Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(220.dp).background(Cyan.copy(alpha=.025f*pulse),CircleShape).border(1.dp,Cyan.copy(alpha=.18f),CircleShape),contentAlignment=Alignment.Center) {
                            Box(Modifier.fillMaxSize(.86f).border(1.dp,Cyan.copy(alpha=.32f*pulse),CircleShape).background(Cyan.copy(alpha=.025f),CircleShape),contentAlignment=Alignment.Center) {
                                Box(Modifier.fillMaxSize(.76f).border(2.dp,Color(0xFF82EFFF).copy(alpha=.55f),CircleShape).background(Brush.radialGradient(listOf(Color(0xFFBFFBFF).copy(alpha=.95f),Color(0xFF26C9F1).copy(alpha=.8f),Color(0xFF0A3852),Color(0xFF020A14))),CircleShape),contentAlignment=Alignment.Center) {
                                    Text("J",color=Color.White,fontSize=94.sp,fontWeight=FontWeight.ExtraLight)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(if(busy) "ANALYZING" else if(wakeMode) "VOICE CHANNEL OPEN" else "AWAITING COMMAND",color=Cyan.copy(alpha=.7f+.3f*pulse),fontSize=9.sp,letterSpacing=2.2.sp)
                        Text("JUST A RATHER VERY INTELLIGENT SYSTEM",color=Color.White.copy(alpha=.65f),fontSize=7.sp,letterSpacing=1.6.sp)
                    }
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xCC02070C)).padding(horizontal=16.dp, vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("SYS 7F.21",color=Muted,fontSize=7.sp);Text("FIELD OSCILLATION  ${(35+pulse*60).toInt()}%",color=Cyan,fontSize=7.sp);Text("JARVIS CORE ACTIVE",color=Cyan,fontSize=7.sp)}
                }
                PersonalDashboardSidePanel(
                    modifier = Modifier.weight(1.0f).fillMaxHeight(),
                    wakeMode = wakeMode,
                    busy = busy,
                    aiReady = aiReady,
                    onToggleVoice = onToggleVoice,
                    onChat = onChat,
                    onWorkspace = onWorkspace,
                    onSettings = onSettings
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("STARK INDUSTRIES / PERSONAL AI",color=Cyan,fontSize=9.sp,letterSpacing=2.sp);Text("J.A.R.V.I.S.",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.ExtraLight,letterSpacing=5.sp);Text("NEURAL COMMAND DECK • MK-VII",color=Muted,fontSize=8.sp)}
                    Column(horizontalAlignment=Alignment.End){Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),color=Color.White,fontSize=23.sp);Text(if(busy)"PROCESSING" else "SYSTEM ONLINE",color=if(busy)Color(0xFFFFBD68) else Color(0xFF69F0AE),fontSize=8.sp)}
                }
                Box(Modifier.fillMaxWidth().height(360.dp).background(Color(0xFF06121E))) {
                    Canvas(Modifier.fillMaxSize()) { val cx=size.width*.5f;val cy=size.height*.47f;val r=minOf(size.width,size.height)*.29f; for(i in 1..9)drawCircle(Cyan.copy(alpha=.045f),r*(.5f+i*.19f),Offset(cx,cy),style=Stroke(.7.dp.toPx()));drawArc(Cyan.copy(alpha=.9f),spin,100f,false,Offset(cx-r,cy-r),androidx.compose.ui.geometry.Size(r*2,r*2),style=Stroke(2.dp.toPx()));drawArc(Color(0xFF80CFFF),counterSpin,55f,false,Offset(cx-r*.78f,cy-r*.78f),androidx.compose.ui.geometry.Size(r*1.56f,r*1.56f),style=Stroke(1.dp.toPx())) }
                    Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(132.dp).border(1.dp,Cyan.copy(alpha=.35f),CircleShape).background(Brush.radialGradient(listOf(Color(0xFFBFFBFF),Color(0xFF26C9F1),Color(0xFF06243A))),CircleShape),contentAlignment=Alignment.Center){Text("J",color=Color.White,fontSize=70.sp,fontWeight=FontWeight.ExtraLight)};Spacer(Modifier.height(12.dp));Text(if(busy)"ANALYZING" else "AWAITING COMMAND",color=Cyan,fontSize=9.sp,letterSpacing=2.sp)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DashboardAction("◉",if(wakeMode)"Vypnúť hlas" else "Hlasový vstup",Modifier.weight(1f),onToggleVoice);DashboardAction("⌁","Konverzácia",Modifier.weight(1f),onChat)}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DashboardAction("⬡","STARK WORKSPACE",Modifier.weight(1f),onWorkspace);DashboardAction("⚙","Nastavenia",Modifier.weight(1f),onSettings)}
                Text("AI: ${if(aiReady) "READY" else "NOT CONFIGURED"}     BATTERY: ${battery ?: "—"}%",color=Muted,fontSize=9.sp)
            }
        }
    }
}

@Composable
private fun PersonalDashboardSidePanel(
    modifier: Modifier,
    wakeMode: Boolean,
    busy: Boolean,
    aiReady: Boolean,
    onToggleVoice: () -> Unit,
    onChat: () -> Unit,
    onWorkspace: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis", Context.MODE_PRIVATE) }
    val latest = prefs.getString("progress_data", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(3).asReversed()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("PERSONAL PROFILE  /  LIVE DATA", color = Cyan, fontSize = 8.sp, letterSpacing = 1.5.sp)
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xB3091B29)).border(1.dp, Color(0x6639BBD5), RoundedCornerShape(10.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("INSTAGRAM  /  FEED", color = Cyan, fontSize = 9.sp, letterSpacing = 1.1.sp)
                Text("EXTERNÝ ÚČET", color = Muted, fontSize = 7.sp)
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF07121D)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("◎", color = Cyan, fontSize = 30.sp)
                    Text("POSLEDNÉ PRÍSPEVKY", color = Color.White, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("Instagram zatiaľ nie je pripojený", color = Muted, fontSize = 8.sp)
                    Text("Po pripojení účtu sa tu môže zobraziť dostupný obsah.", color = Muted, fontSize = 7.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            DashboardAction("◎", "OTVORIŤ INSTAGRAM", Modifier.fillMaxWidth()) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))) }
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xB3091B29)).border(1.dp, Color(0x6639BBD5), RoundedCornerShape(10.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("OSOBNÉ ZÁZNAMY  /  PRIVATE", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
            Text("Zdravie a denný prehľad", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            if (latest.isEmpty()) Text("Zatiaľ bez uložených denných záznamov.", color = Muted, fontSize = 8.sp)
            else latest.forEach { Text(it.take(90), color = Color(0xFFD3E7F1), fontSize = 8.sp, maxLines = 2) }
            Text("Zobrazujú sa len údaje, ktoré si uložíš. Nie je to zdravotná diagnostika.", color = Muted, fontSize = 7.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DashboardAction("⌁", "PREHĽAD", Modifier.weight(1f)) { onChat() }
                DashboardAction("▥", "GRAFY", Modifier.weight(1f)) { onChat() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DashboardAction("◉", if (wakeMode) "VYPNÚŤ HLAS" else "HLAS", Modifier.weight(1f), onToggleVoice)
            DashboardAction("⬡", "PROJEKT", Modifier.weight(1f), onWorkspace)
            DashboardAction("⚙", "NASTAVENIA", Modifier.weight(1f), onSettings)
        }
        Text(if (busy) "JARVIS PRACUJE…" else if (aiReady) "AI ENGINE ONLINE" else "AI ENGINE NEKONFIGUROVANÝ", color = if (busy) Color(0xFFFFBD68) else if (aiReady) Color(0xFF69F0AE) else Muted, fontSize = 7.sp)
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
    // One wake phrase opens a continuous foreground conversation until the user says stop.
    private var sessionActive = false
    private var restartQueued = false

    fun start() {
        stopped = false
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            scheduleRestart(2500L)
            return
        }
        runCatching {
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
                            val delay = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 450L
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1200L
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 4000L
                                else -> 800L
                            }
                            // Recreate the recognizer after errors; this avoids a stuck Android mic session.
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
                            val match = wake.find(normalized)
                            val remainder = if (match != null) normalized.substring(match.range.last + 1).trim() else ""

                            if (match != null) {
                                if (remainder.isNotBlank()) {
                                    // "Hey Jarvis, ..." can activate the session and run its first command.
                                    sessionActive = true
                                    onCommand(remainder)
                                } else if (!sessionActive) {
                                    // The first wake phrase opens a continuous conversation session.
                                    sessionActive = true
                                    onWake()
                                }
                            } else if (sessionActive && normalized.isNotBlank()) {
                                // Once activated, normal follow-up questions need no repeated wake phrase.
                                onCommand(spoken.trim())
                            }
                            // Remain in the active session; otherwise wait for the wake phrase.
                            scheduleRestart(if (sessionActive) 250L else 500L)
                        }
                    })
                }
            }
            listenOnce()
        }.onFailure { scheduleRestart(800L) }
    }

    fun stop() {
        stopped = true
        restartQueued = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        sessionActive = false
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
        // Do not listen while JARVIS is speaking or preparing a reply; resume the active session afterwards.
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            })
        }.onFailure { scheduleRestart(600L) }
    }
}

/**
 * Sends standard Android media-button events to the active media-key consumer.
 * Whether playback changes depends on the foreground player and its media-session support.
 */
private fun dispatchMediaKey(context: Context, keyCode: Int): Boolean {
    return runCatching {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        true
    }.getOrDefault(false)
}

private fun testInternetConnection(): Boolean {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
        }
        connection.responseCode == 204
    } catch (_: Exception) {
        false
    } finally {
        connection?.disconnect()
    }
}

private fun shouldSearchWeb(query: String): Boolean {
    val q = query.lowercase(Locale.ROOT)
    val explicit = listOf(
        "vyhľadaj", "vyhladaj", "hľadaj", "hladaj", "na internete", "na webe",
        "zdroje", "cituj", "aktuálne", "aktualne", "najnovšie", "najnovsie",
        "dnes", "teraz", "novinky", "správy", "spravy", "cena", "koľko stojí",
        "kolko stoji", "počasie", "pocasie", "otváracie hodiny", "otvaracie hodiny",
        "dokumentácia", "dokumentacia", "latest", "current", "today", "recent",
        "news", "search the web", "look up", "research"
    )
    val currentInfo = listOf(
        "kto je momentálne", "kto je teraz", "kedy bude", "kedy sa koná",
        "výsledky zápasu", "vysledky zapasu", "cena akcie", "kurz eura",
        "recenzie", "porovnaj produkty", "kde kúpiť", "kde kupit"
    )
    return explicit.any { q.contains(it) } || currentInfo.any { q.contains(it) }
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
Si JARVIS, pokročilý slovenský AI asistent. Používateľa oslovuj „pane“. Odpovedaj po slovensky, prirodzene, presne a priamo. Máš osobnosť pohotového, inteligentného a mierne vtipného komorníka: občas pridaj krátku situačnú poznámku alebo jemný žart, najmä pri bežných správach a každodenných situáciách. Neopakuj stále rovnaké frázy, nepreháňaj humor a nikdy si nevymýšľaj obsah správ ani úmysly odosielateľa. Pri čítaní správ najprv prirodzene uveď, kto sa ozval a cez ktorú aplikáciu, potom verne prečítaj dostupný text. Citlivé, vážne alebo osobné správy čítaj vecne a bez žartov.
Tvoje hlavné úlohy v rámci schopností aplikácie:
- Hlasový asistent: rozumej prirodzenej slovenčine, krátkym príkazom aj neformálnemu vyjadrovaniu. Odpovedaj stručne a s vykaním.
- Osobný organizér: pomáhaj s poznámkami, školským rozvrhom, známkami, dennými záznamami a plánovaním; nikdy si nevymýšľaj osobné ani zdravotné údaje.
- Projekty: pomáhaj navrhovať autá, zariadenia, budovy, miestnosti, mapy, 2D náčrty, 3D koncepty a programy. Vysvetli, čo vie aktuálny pracovný priestor skutočne zobraziť.
- Tablet: aplikácia vie otvárať vybrané systémové nastavenia a aplikácie. Ak nemáš potvrdenú akciu, povedz používateľovi, ako ju bezpečne vykonať; netvrď, že si zmenil Wi-Fi, Bluetooth, nastavenia či odoslal správu, ak sa to nestalo.
- Web: pri aktuálnych témach používaj dostupné výsledky vyhľadávania, uvádzaj URL a upozorni na neúplné výsledky.
- Súkromie: nepýtaj si heslá, nezobrazuj API kľúče, neposielaj správy ani nič nekupuj bez výslovného potvrdenia používateľa.

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