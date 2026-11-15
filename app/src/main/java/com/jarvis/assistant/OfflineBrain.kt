package com.jarvis.assistant

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Local JARVIS language model.
 * Gemma 4 E2B is selected on devices with at least 8 GB physical RAM.
 * Smaller devices retain the already bundled Qwen3 1.7B fallback.
 */
class OfflineBrain(private val context: Context) {
    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_LABEL = "Gemma 4 E2B (2,59 GB; zariadenia s aspoň 8 GB RAM)"
        const val FALLBACK_MODEL_FILE = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
        private const val GEMMA_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        private const val GEMMA_BYTES = 2_588_147_712L
        private const val GEMMA_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        private const val QWEN_MIN_BYTES = 850L * 1024L * 1024L
        private const val GEMMA_MIN_FREE_BYTES = 3_200L * 1024L * 1024L
        private const val MIN_RAM_FOR_GEMMA = 8L * 1024L * 1024L * 1024L
        private const val MODEL_CHOICE_PREF = "jarvis_offline_model_choice"
        private const val GEMMA_VERIFIED_SIZE_PREF = "jarvis_gemma_verified_size"
        private const val GEMMA_VERIFIED_MODIFIED_PREF = "jarvis_gemma_verified_modified"
        private val preparationMutex = Mutex()
    }

    private fun totalRamBytes(): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem
    }

    private fun gemmaSupported(): Boolean = totalRamBytes() >= MIN_RAM_FOR_GEMMA

    private fun useQwenFallback(): Boolean =
        !gemmaSupported() ||
            context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                .getString(MODEL_CHOICE_PREF, "gemma").equals("qwen", true)

    private val modelFile: File
        get() = File(context.filesDir, if (useQwenFallback()) FALLBACK_MODEL_FILE else MODEL_FILE)

    private fun expectedMinimumBytes(): Long =
        if (useQwenFallback()) QWEN_MIN_BYTES else GEMMA_BYTES

    fun isPrepared(): Boolean {
        val file = modelFile
        if (!file.exists() || file.length() < expectedMinimumBytes()) return false
        if (useQwenFallback()) return true

        // SHA-256 is checked when Gemma is first accepted. Cache that verification
        // against file size + modification time so we do not hash 2.6 GB on every chat turn.
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        return prefs.getLong(GEMMA_VERIFIED_SIZE_PREF, -1L) == file.length() &&
            prefs.getLong(GEMMA_VERIFIED_MODIFIED_PREF, -1L) == file.lastModified()
    }

    private fun markGemmaVerified(file: File) {
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit()
            .putLong(GEMMA_VERIFIED_SIZE_PREF, file.length())
            .putLong(GEMMA_VERIFIED_MODIFIED_PREF, file.lastModified())
            .apply()
    }

    /** True when a local model is already prepared or a bundled fallback can be prepared. */
    fun isInstalled(): Boolean =
        isPrepared() ||
            runCatching { context.assets.open(FALLBACK_MODEL_FILE).use { true } }.getOrDefault(false)

    private fun copyBundledQwen(onProgress: (Int) -> Unit) {
        val destination = File(context.filesDir, FALLBACK_MODEL_FILE)
        if (destination.exists() && destination.length() >= QWEN_MIN_BYTES) {
            onProgress(100)
            return
        }
        if (context.filesDir.usableSpace < 1_050L * 1024L * 1024L) {
            throw IllegalStateException("Na prípravu záložnej offline AI je potrebných aspoň 1,05 GB voľného miesta.")
        }
        val temp = File(context.filesDir, "$FALLBACK_MODEL_FILE.part")
        try {
            context.assets.open(FALLBACK_MODEL_FILE).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(((copied * 100) / (932L * 1024L * 1024L)).toInt().coerceIn(0, 99))
                    }
                    output.fd.sync()
                }
            }
            if (temp.length() < QWEN_MIN_BYTES) throw IllegalStateException("Záložný Qwen3 model v APK je neúplný.")
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            onProgress(100)
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadGemma(onProgress: (Int) -> Unit) {
        val destination = File(context.filesDir, MODEL_FILE)
        if (destination.exists() && destination.length() >= GEMMA_BYTES) {
            if (sha256(destination).equals(GEMMA_SHA256, ignoreCase = true)) {
                markGemmaVerified(destination)
                onProgress(100)
                return
            }
            android.util.Log.w("JARVIS-OfflineAI", "Existing Gemma 4 file failed SHA-256 verification; downloading a clean copy.")
            destination.delete()
        }
        if (context.filesDir.usableSpace < GEMMA_MIN_FREE_BYTES) {
            throw IllegalStateException("Gemma 4 potrebuje približne 3,2 GB voľného miesta na stiahnutie a prípravu.")
        }
        val temporary = File(context.filesDir, "$MODEL_FILE.part")
        val connection = (URL(GEMMA_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "JARVIS-Android")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Stiahnutie Gemma 4 zlyhalo (HTTP ${connection.responseCode})."
            }
            val advertisedLength = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val denominator = if (advertisedLength > 0) advertisedLength else GEMMA_BYTES
                        onProgress(((copied * 100) / denominator).toInt().coerceIn(0, 99))
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() >= GEMMA_BYTES) {
                "Stiahnutý súbor Gemma 4 je neúplný (${temporary.length()} bajtov)."
            }
            check(sha256(temporary).equals(GEMMA_SHA256, ignoreCase = true)) {
                "Kontrola SHA-256 modelu Gemma 4 zlyhala. Súbor nebol použitý."
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Nepodarilo sa uložiť model Gemma 4." }
            markGemmaVerified(destination)
            onProgress(100)
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    /** Downloads Gemma 4 automatically on compatible devices; otherwise prepares bundled Qwen3. */
    suspend fun download(onProgress: (Int) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        preparationMutex.withLock {
            if (isPrepared()) {
                onProgress(100)
                return@withLock
            }
            if (!useQwenFallback()) {
                try {
                    downloadGemma(onProgress)
                    context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                        .edit().putString(MODEL_CHOICE_PREF, "gemma").apply()
                    return@withLock
                } catch (error: Exception) {
                    android.util.Log.w("JARVIS-OfflineAI", "Gemma 4 download failed; using bundled Qwen3 fallback.", error)
                    context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                        .edit().putString(MODEL_CHOICE_PREF, "qwen").apply()
                }
            }
            copyBundledQwen(onProgress)
        }
    }

    /**
     * GPU is attempted first; CPU is a compatibility fallback.
     * Gemma 4's LiteRT-LM model file supports both backends.
     */
    private fun createInitializedEngine(): Engine {
        var gpuError: Throwable? = null
        try {
            val gpuEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            )
            gpuEngine.initialize()
            return gpuEngine
        } catch (error: Exception) {
            gpuError = error
            android.util.Log.w("JARVIS-OfflineAI", "GPU initialization failed; trying CPU.", error)
        } catch (error: LinkageError) {
            gpuError = error
            android.util.Log.w("JARVIS-OfflineAI", "GPU native runtime unavailable; trying CPU.", error)
        }

        try {
            val cpuEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            )
            cpuEngine.initialize()
            return cpuEngine
        } catch (cpuError: Exception) {
            android.util.Log.e("JARVIS-OfflineAI", "CPU initialization failed.", cpuError)
            throw IllegalStateException(
                "AI sa nepodarilo spustiť cez GPU ani CPU. GPU: ${gpuError?.message ?: "neznáma chyba"}. CPU: ${cpuError.message ?: "neznáma chyba"}",
                cpuError
            )
        } catch (cpuError: LinkageError) {
            android.util.Log.e("JARVIS-OfflineAI", "CPU native runtime unavailable.", cpuError)
            throw IllegalStateException(
                "LiteRT runtime sa nepodarilo načítať. GPU: ${gpuError?.message ?: "neznáma chyba"}. CPU: ${cpuError.message ?: "neznáma chyba"}",
                cpuError
            )
        }
    }

    /** All chat history and personal context remain on the device. */
    suspend fun answer(prompt: String, extraContext: String = ""): String = withContext(Dispatchers.IO) {
        download()
        check(isPrepared()) { "Lokálny AI model nie je pripravený." }

        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val memory = (
            prefs.getString("memories", "").orEmpty() +
                "\nImportované rozhovory používateľa (lokálny kontext):\n" +
                prefs.getString("imported_chat_context", "").orEmpty() +
                "\n" + JarvisPersonalProfiles.promptContext(context) +
                "\n" + JarvisProtection.promptRules(context)
            ).takeLast(7000)

        val history = runCatching {
            val saved = JSONArray(prefs.getString("chat_history", "[]").orEmpty())
            val recent = (maxOf(0, saved.length() - 10) until saved.length()).mapNotNull { index ->
                val item = saved.optJSONObject(index) ?: return@mapNotNull null
                val role = if (item.optBoolean("jarvis", true)) "JARVIS" else "POUŽÍVATEĽ"
                val text = item.optString("text").trim().take(700)
                if (text.isBlank()) null else "$role: $text"
            }
            recent.joinToString("\n")
        }.getOrDefault("")

        val engine = try {
            createInitializedEngine()
        } catch (gemmaError: Exception) {
            if (useQwenFallback()) throw gemmaError
            android.util.Log.e("JARVIS-OfflineAI", "Gemma 4 runtime failed; switching to bundled Qwen3.", gemmaError)
            prefs.edit().putString(MODEL_CHOICE_PREF, "qwen").apply()
            // Free the large Gemma file before copying the bundled fallback.
            runCatching { File(context.filesDir, MODEL_FILE).delete() }
            copyBundledQwen {}
            createInitializedEngine()
        }
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "Si JARVIS, všeobecný konverzačný AI asistent na úrovni univerzálneho jazykového asistenta. Používateľa oslovuj pane. " +
                            "Nie si obmedzený na zoznam naprogramovaných tém ani príkazov. Odpovedaj na ľubovoľnú zmysluplnú otázku a prirodzene pokračuj v rozhovore o novej téme. " +
                            "Rozumej prirodzenej slovenčine, hovorovým výrazom, neúplným vetám, preklepom a kontextu predchádzajúcej konverzácie. " +
                            "Poskytuj vysvetlenia, rady, porovnania, nápady, plánovanie, doučovanie, technickú pomoc a analýzu situácií naprieč témami. " +
                            "Ak používateľ opisuje osobnú situáciu, najprv reaguj na konkrétny obsah a potom ponúkni premyslenú radu; nepremieňaj každú vetu na príkaz. " +
                            "Ak chýba kľúčový údaj, polož jednu stručnú doplňujúcu otázku. Ak odpoveď závisí od aktuálnych faktov, priznaj potrebu aktuálneho vyhľadania; nevydávaj staré znalosti za živé údaje. " +
                            "Pri náročnom uvažovaní postupuj dôkladne, ale zobraz jasný záver a podstatné dôvody. " +
                            "Buď presný, nevymýšľaj fakty, zdroje, merania, diagnózy ani výsledky akcií a priznaj neistotu. " +
                            "V bežnom rozhovore používaj prirodzený situačný humor; pri vážnych, zdravotných a bezpečnostných témach bez humoru. " +
                            JarvisIntelligence.SYSTEM_PROMPT_EXTENSION + "\n" +
                            "AKTUÁLNY KOMUNIKAČNÝ REŽIM: " + when (prefs.getString("jarvis_mood", "PROFESIONÁLNY")) {
                                "POKOJNÝ" -> "pokojný, trpezlivý, jemný a stručný"
                                "UVOĽNENÝ" -> "uvoľnený, prirodzený, so situačným humorom; vážne témy bez humoru"
                                "SÚSTREDENÝ" -> "sústredený na cieľ, presný, minimum odbočiek"
                                "CITLIVÝ" -> "citlivý, pozorný, vecný a bez humoru"
                                else -> "profesionálny, pokojný, kompetentný a prirodzený"
                            } + ". Režim prispôsobuje štýl odpovede, nie fakty ani bezpečnostné pravidlá.\\n" +
                            JarvisProtection.promptRules(context) + "\\n" +
                            JarvisPersonalProfiles.promptContext(context) + "\n" +
                            "Používateľova lokálna pamäť (použi iba ak je relevantná):\n" +
                            memory.ifBlank { "Zatiaľ bez uložených preferencií." } +
                            (if (extraContext.isBlank()) "" else "\nDoplňujúci kontext:\n${extraContext.take(3500)}") +
                            "\nNedávny rozhovor:\n" + history.ifBlank { "Žiadny." }
                    ),
                    samplerConfig = SamplerConfig(temperature = 0.55, topK = 40, topP = 0.92)
                )
            )
            try {
                conversation.sendMessage(prompt).toString()
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
        }
    }
}
