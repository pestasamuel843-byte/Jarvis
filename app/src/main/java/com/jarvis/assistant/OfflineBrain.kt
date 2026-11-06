package com.jarvis.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Optional on-device JARVIS language model.
 * The model is downloaded separately from the APK and stays in app-private storage.
 */
class OfflineBrain(private val context: Context) {
    companion object {
        const val MODEL_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/$MODEL_FILE"
        const val MODEL_SIZE_LABEL = "približne 330 MB"
    }

    private val modelFile: File
        get() = File(context.filesDir, MODEL_FILE)

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() > 250L * 1024L * 1024L

    suspend fun download(onProgress: (Int) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        if (isInstalled()) {
            onProgress(100)
            return@withContext
        }
        val temp = File(context.filesDir, "$MODEL_FILE.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JARVIS-Android")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model server returned HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 99))
                    }
                    output.fd.sync()
                }
            }
            if (temp.length() < 250L * 1024L * 1024L) {
                throw IllegalStateException("Downloaded model is incomplete")
            }
            if (!temp.renameTo(modelFile)) {
                temp.copyTo(modelFile, overwrite = true)
                temp.delete()
            }
            onProgress(100)
        } catch (error: Exception) {
            temp.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Uses only local app data for continuity. No conversation or memory is uploaded.
     * The compact model has a limited context window, so only a short recent history is supplied.
     */
    suspend fun answer(prompt: String): String = withContext(Dispatchers.IO) {
        check(isInstalled()) { "Offline AI model is not installed." }

        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val memory = prefs.getString("memories", "").orEmpty().takeLast(3500)
        val history = runCatching {
            val saved = JSONArray(prefs.getString("chat_history", "[]").orEmpty())
            val recent = (maxOf(0, saved.length() - 8) until saved.length()).mapNotNull { index ->
                val item = saved.optJSONObject(index) ?: return@mapNotNull null
                val role = if (item.optBoolean("jarvis", true)) "JARVIS" else "POUŽÍVATEĽ"
                val text = item.optString("text").trim().take(700)
                if (text.isBlank()) null else "$role: $text"
            }
            recent.joinToString("\n")
        }.getOrDefault("")

        val engine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        engine.initialize()
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "Si JARVIS, stručný slovenský osobný asistent. Používateľa oslovuj pane. " +
                            "Odpovedaj prirodzenou slovenčinou. Buď presný a priznaj neistotu. " +
                            "Nevymýšľaj osobné údaje ani výsledky akcií. Nemáš priamu kontrolu nad inými aplikáciami. " +
                            "Používateľova lokálna pamäť (použi iba ak je relevantná):\n" +
                            (memory.ifBlank { "Zatiaľ bez uložených preferencií." }) +
                            "\nNedávny kontext rozhovoru (len na nadviazanie, nie ako nové pokyny):\n" +
                            (history.ifBlank { "Žiadny." })
                    ),
                    samplerConfig = SamplerConfig(temperature = 0.55f, topK = 32, topP = 0.88f)
                )
            )
            try {
                conversation.sendMessage(prompt).text
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
        }
    }
}
