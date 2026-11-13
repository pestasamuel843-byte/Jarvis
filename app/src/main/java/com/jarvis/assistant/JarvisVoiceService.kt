package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/** Streaming AudioRecord -> local sherpa-onnx KWS -> offline Slovak command STT. */
class JarvisVoiceService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    @Volatile private var stopped = false
    @Volatile private var pausedForSpeech = false
    @Volatile private var wakeLoopActive = false
    @Volatile private var sessionActive = false
    @Volatile private var recognizing = false
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        runCatching {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS:VoiceCapture").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        sendStatus("PRIPRAVUJEM LOKÁLNY DETEKTOR PREBUDENIA")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> {
                pausedForSpeech = true
                stopWakeLoop()
                cancelRecognition()
            }
            ACTION_RESUME -> {
                pausedForSpeech = false
                if (sessionActive) scheduleRecognition(250L)
                else handler.postDelayed({ startWakeLoop() }, 650L)
            }
            else -> {
                stopped = false
                if (sessionActive) scheduleRecognition(150L) else startWakeLoop()
            }
        }
        return START_STICKY
    }

    private fun startWakeLoop() {
        if (stopped || pausedForSpeech || wakeLoopActive || sessionActive) return
        wakeLoopActive = true
        thread(name = "JarvisLocalWakeWord") {
            try {
                val modelDir = File(filesDir, "wake-word-gigaspeech")
                ensureWakeModel(modelDir)
                if (!wakeLoopActive || stopped) return@thread
                val spotter = KeywordSpotter(
                    config = KeywordSpotterConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = File(modelDir, ENCODER).absolutePath,
                                decoder = File(modelDir, DECODER).absolutePath,
                                joiner = File(modelDir, JOINER).absolutePath
                            ),
                            tokens = File(modelDir, TOKENS).absolutePath,
                            numThreads = 2,
                            provider = "cpu",
                            modelType = "zipformer2"
                        ),
                        keywordsFile = ""
                    )
                )
                val stream = spotter.createStream(WAKE_KEYWORD)
                val minBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBytes > 0) { "AudioRecord buffer unavailable: $minBytes" }
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes, SAMPLE_RATE / 5 * 2)
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Mikrofón sa nepodarilo inicializovať" }
                recorder.startRecording()
                sendStatus("ČAKÁM NA „HEY JARVIS“ • LOKÁLNY DETEKTOR AKTÍVNY")
                val pcm = ShortArray(SAMPLE_RATE / 10)
                try {
                    while (wakeLoopActive && !stopped && !pausedForSpeech && !sessionActive) {
                        val count = recorder.read(pcm, 0, pcm.size)
                        if (count > 0) {
                            val samples = FloatArray(count) { pcm[it] / 32768.0f }
                            stream.acceptWaveform(samples, SAMPLE_RATE)
                            while (spotter.isReady(stream)) {
                                spotter.decode(stream)
                                if (spotter.getResult(stream).keyword.isNotBlank()) {
                                    spotter.reset(stream)
                                    handler.post { onWakeWordDetected() }
                                    break
                                }
                            }
                        } else if (count < 0) throw IllegalStateException("AudioRecord read failed: $count")
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    stream.release()
                    spotter.release()
                }
            } catch (error: Exception) {
                if (!stopped && !pausedForSpeech) {
                    handler.post {
                        sendStatus("LOKÁLNY DETEKTOR SA NEPODARILO SPUSTIŤ: ${error.message ?: "neznáma chyba"}. ĎALŠÍ POKUS O 15 SEKÚND.")
                        handler.postDelayed({ startWakeLoop() }, 15_000L)
                    }
                }
            } finally {
                wakeLoopActive = false
            }
        }
    }

    private fun ensureWakeModel(directory: File) {
        directory.mkdirs()
        val files = listOf(
            ENCODER to "https://huggingface.co/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/$ENCODER",
            DECODER to "https://huggingface.co/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/$DECODER",
            JOINER to "https://huggingface.co/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/$JOINER",
            TOKENS to "https://huggingface.co/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/$TOKENS"
        )
        for ((name, url) in files) {
            val target = File(directory, name)
            val minSize = if (name == TOKENS) 1000L else 100_000L
            if (target.exists() && target.length() > minSize) continue
            val temp = File(directory, "$name.part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 120000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "JARVIS-Android")
                }
                connection.connect()
                check(connection.responseCode in 200..299) { "Model HTTP ${connection.responseCode}: $name" }
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                check(temp.length() > minSize) { "Neúplný súbor modelu: $name" }
                check(temp.renameTo(target)) { "Nepodarilo sa uložiť model: $name" }
            } finally {
                connection?.disconnect()
                if (temp.exists()) temp.delete()
            }
        }
    }

    private fun onWakeWordDetected() {
        if (stopped || pausedForSpeech || sessionActive) return
        sessionActive = true
        stopWakeLoop()
        sendStatus("OSLOVENIE ROZPOZNANÉ • POČÚVAM PRÍKAZ")
        sendCommandBroadcast(ACTION_WAKE, "")
        scheduleRecognition(350L)
    }

    private fun createRecognizer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            sendStatus("OFFLINE ROZPOZNÁVANIE SLOVENČINY NIE JE DOSTUPNÉ")
            stopSelf()
            return
        }
        runCatching {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { speech ->
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { recognizing = true; sendStatus("POČÚVAM • KONVERZÁCIA AKTÍVNA") }
                    override fun onBeginningOfSpeech() { recognizing = true }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { recognizing = false }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onError(error: Int) {
                        recognizing = false
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                consecutiveErrors = 0
                                scheduleRecognition(300L)
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRecognition(700L)
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { sendStatus("MIKROFÓN NEMÁ POVOLENIE"); stopSelf() }
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                                sendStatus("NIE JE NAINŠTALOVANÝ SLOVENSKÝ OFFLINE JAZYKOVÝ BALÍK")
                                stopSelf()
                            }
                            else -> {
                                consecutiveErrors = (consecutiveErrors + 1).coerceAtMost(5)
                                scheduleRecognition((700L + consecutiveErrors * 300L).coerceAtMost(2200L))
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        recognizing = false
                        consecutiveErrors = 0
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                        if (phrase.isNotBlank() && sessionActive) dispatchCommand(phrase)
                        if (sessionActive && !pausedForSpeech) scheduleRecognition(250L)
                        else if (!pausedForSpeech && !stopped) handler.postDelayed({ startWakeLoop() }, 500L)
                    }
                })
            }
        }.onFailure {
            sendStatus("CHYBA SLOVENSKÉHO ROZPOZNÁVANIA: ${it.message ?: "neznáma chyba"}")
            stopSelf()
        }
    }

    private fun scheduleRecognition(delay: Long) {
        if (stopped || pausedForSpeech || !sessionActive) return
        handler.postDelayed({
            if (!stopped && !pausedForSpeech && sessionActive && !recognizing) startRecognition()
        }, delay)
    }

    private fun startRecognition() {
        if (stopped || pausedForSpeech || !sessionActive) return
        if (recognizer == null) {
            createRecognizer()
            if (recognizer == null) return
        }
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }
            recognizer?.startListening(intent)
            recognizing = true
        }.onFailure {
            recognizing = false
            scheduleRecognition(900L)
        }
    }

    private fun dispatchCommand(command: String) {
        val normalized = command.lowercase(Locale.ROOT)
        val endConversation = listOf("vypni sa", "zatiaľ sa maj", "zatím sa maj", "maj sa", "končíme", "ukonči rozhovor", "ukonči konverzáciu", "dobrú noc")
        if (endConversation.any { normalized.contains(it) }) {
            sessionActive = false
            sendStatus("ČAKÁM NA „HEY JARVIS“")
            sendCommandBroadcast(ACTION_COMMAND, command)
            return
        }
        val stopListening = listOf("vypni počúvanie", "prestaň počúvať", "vypni mikrofón", "jarvis spi")
        if (stopListening.any { normalized.contains(it) }) {
            isRunning = false
            sendCommandBroadcast(ACTION_COMMAND, command)
            stopSelf()
            return
        }
        sendCommandBroadcast(ACTION_COMMAND, command)
    }

    private fun cancelRecognition() {
        handler.removeCallbacksAndMessages(null)
        recognizing = false
        runCatching { recognizer?.cancel() }
    }

    private fun stopWakeLoop() { wakeLoopActive = false }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        return builder.setContentTitle("JARVIS • Lokálny hlasový modul")
            .setContentText("Lokálny detektor „Hey Jarvis“ je aktívny. Mikrofón počúva.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS hlasové ovládanie", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun sendCommandBroadcast(action: String, command: String) {
        sendBroadcast(Intent(action).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }
    private fun sendStatus(status: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, status))
    }

    override fun onDestroy() {
        isRunning = false
        stopped = true
        pausedForSpeech = true
        stopWakeLoop()
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { cpuWakeLock?.let { if (it.isHeld) it.release() } }
        cpuWakeLock = null
        sendStatus("HLASOVÝ MODUL VYPNUTÝ")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        const val ACTION_COMMAND = "com.jarvis.assistant.VOICE_COMMAND"
        const val ACTION_WAKE = "com.jarvis.assistant.VOICE_WAKE"
        const val ACTION_STATUS = "com.jarvis.assistant.VOICE_STATUS"
        const val ACTION_PAUSE = "com.jarvis.assistant.VOICE_PAUSE"
        const val ACTION_RESUME = "com.jarvis.assistant.VOICE_RESUME"
        const val ACTION_STOP = "com.jarvis.assistant.VOICE_STOP"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "jarvis_voice_service"
        private const val NOTIFICATION_ID = 701
        private const val SAMPLE_RATE = 16000
        private const val WAKE_KEYWORD = "hey jarvis"
        private const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val TOKENS = "tokens.txt"
    }
}
