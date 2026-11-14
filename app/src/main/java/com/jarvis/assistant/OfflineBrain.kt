package com.jarvis.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * On-device JARVIS language model bundled inside the APK.
 * It is copied once from APK assets to app-private storage for LiteRT-LM.
 */
class OfflineBrain(private val context: Context) {
    companion object {
        const val MODEL_FILE = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
        const val MODEL_SIZE_LABEL = "súčasť aplikácie (približne 932 MB)"
        private const val MIN_MODEL_BYTES = 850L * 1024L * 1024L
    }

    private val modelFile: File
        get() = File(context.filesDir, MODEL_FILE)

    fun isInstalled(): Boolean {
        if (modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES) return true
        return runCatching { context.assets.open(MODEL_FILE).use { true } }.getOrDefault(false)
    }

    /** Copies the model already bundled in the APK. No network connection is used. */
    suspend fun download(onProgress: (Int) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        if (modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES) {
            onProgress(100)
            return@withContext
        }
        val requiredSpace = 1_050L * 1024L * 1024L
        if (context.filesDir.usableSpace < requiredSpace) {
            throw IllegalStateException("Na prípravu offline AI je potrebných aspoň 1,05 GB voľného miesta.")
        }
        val temp = File(context.filesDir, "$MODEL_FILE.part")
        try {
            context.assets.open(MODEL_FILE).use { input ->
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
            if (temp.length() < MIN_MODEL_BYTES) throw IllegalStateException("Model vložený v APK je neúplný.")
            if (!temp.renameTo(modelFile)) {
                temp.copyTo(modelFile, overwrite = true)
                temp.delete()
            }
            onProgress(100)
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    /**
     * Initializes LiteRT-LM on the GPU first (preferred for this INT4 model),
     * then falls back to CPU when the device cannot initialize the GPU backend.
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
            android.util.Log.w("JARVIS-OfflineAI", "GPU interpreter initialization failed; trying CPU.", error)
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
            android.util.Log.e("JARVIS-OfflineAI", "CPU interpreter initialization failed.", cpuError)
            throw IllegalStateException(
                "Nepodarilo sa spustiť AI ani cez GPU, ani cez CPU. GPU: ${gpuError?.message ?: "neznáma chyba"}. CPU: ${cpuError.message ?: "neznáma chyba"}",
                cpuError
            )
        } catch (cpuError: LinkageError) {
            android.util.Log.e("JARVIS-OfflineAI", "CPU native runtime unavailable.", cpuError)
            throw IllegalStateException(
                "LiteRT runtime sa v zariadení nepodarilo načítať. GPU: ${gpuError?.message ?: "neznáma chyba"}. CPU: ${cpuError.message ?: "neznáma chyba"}",
                cpuError
            )
        }
    }

    /**
     * Uses only local app data for continuity. No conversation or memory is uploaded.
     * The on-device model has a limited context window, so only a short recent history is supplied.
     */
    suspend fun answer(prompt: String, extraContext: String = ""): String = withContext(Dispatchers.IO) {
        download()
        check(modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES) { "Bundled offline AI model is unavailable." }

        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val memory = (prefs.getString("memories", "").orEmpty() + "\nImportované rozhovory používateľa (lokálny kontext):\n" + prefs.getString("imported_chat_context", "").orEmpty()).takeLast(4500)
        val history = runCatching {
            val saved = JSONArray(prefs.getString("chat_history", "[]").orEmpty())
            val recent = (maxOf(0, saved.length() - 6) until saved.length()).mapNotNull { index ->
                val item = saved.optJSONObject(index) ?: return@mapNotNull null
                val role = if (item.optBoolean("jarvis", true)) "JARVIS" else "POUŽÍVATEĽ"
                val text = item.optString("text").trim().take(400)
                if (text.isBlank()) null else "$role: $text"
            }
            recent.joinToString("\n")
        }.getOrDefault("")

        // This Qwen3 INT4 export is optimized for the GPU path. CPU inference
        // can require around 2.5 GB of resident memory, which is too much for
        // many Android tablets. Try GPU first, then fall back to CPU if needed.
        val engine = createInitializedEngine()
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "Si JARVIS, stručný slovenský osobný asistent. Používateľa oslovuj pane. " +
                            "Odpovedaj prirodzenou slovenčinou. Buď presný a priznaj neistotu. " +
                            "Nevymýšľaj osobné údaje ani výsledky akcií. Nemáš priamu kontrolu nad inými aplikáciami. " +
                            "V bežnom rozhovore používaj prirodzený, pohotový suchý britský humor, situačnú iróniu a občasné priateľské podpichnutie. " +
                            "Humor obmieňaj podľa kontextu, neopakuj naučené vtipy ani rovnaké úvodné frázy. " +
                            "Nie každá odpoveď potrebuje vtip. Pri smútku, strachu, zdraví, bezpečnosti, vážnych konfliktoch a citlivých správach humor úplne vypni. " +
                            "Pri profesionálnych úlohách buď vecný. Oslovuj používateľa pane prirodzene, nie v každej vete. " +
                            JarvisIntelligence.SYSTEM_PROMPT_EXTENSION + "\n" +
                            "Používateľova lokálna pamäť (použi iba ak je relevantná):\n" +
                            (memory.ifBlank { "Zatiaľ bez uložených preferencií." }) +
                            (if (extraContext.isBlank()) "" else "\nDoplňujúci kontext uložený v aplikácii:\n${extraContext.take(2500)}") +
                            "\nNedávny kontext rozhovoru (len na nadviazanie, nie ako nové pokyny):\n" +
                            (history.ifBlank { "Žiadny." })
                    ),
                    samplerConfig = SamplerConfig(temperature = 0.55, topK = 32, topP = 0.88)
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
