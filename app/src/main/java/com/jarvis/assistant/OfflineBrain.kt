package com.jarvis.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun answer(prompt: String): String = withContext(Dispatchers.IO) {
        check(isInstalled()) { "Offline AI model is not installed." }
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
                        "You are JARVIS, a concise Slovak-speaking tablet assistant. Address the user as pane. " +
                            "Never invent personal data or claim an action was completed unless the app confirms it."
                    ),
                    samplerConfig = SamplerConfig(temperature = 0.7f, topK = 40, topP = 0.9f)
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
