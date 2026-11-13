package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Visible, user-enabled foreground microphone service.
 * SpeechRecognizer is a one-utterance API, so normal end-of-speech/time-out
 * results are restarted quickly. Longer recovery delays are reserved for real errors.
 */
class JarvisVoiceService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = false
    private var listening = false
    private var sessionActive = false
    private var pausedForSpeech = false
    private var restartQueued = false
    private var usingOnDeviceRecognizer = false
    private var consecutiveErrors = 0
    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Keep CPU awake while the user has explicitly enabled background listening.
        runCatching {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS:VoiceListening").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        createRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pausedForSpeech = true
                cancelRecognition()
            }
            ACTION_RESUME -> {
                pausedForSpeech = false
                scheduleRestart(250L)
            }
            else -> {
                stopped = false
                scheduleRestart(150L)
            }
        }
        return START_STICKY
    }

    private fun createRecognizer(preferOnDevice: Boolean = true) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendStatus("ROZPOZNÁVANIE REČI NIE JE DOSTUPNÉ")
            scheduleRestart(3000L)
            return
        }
        runCatching {
            recognizer?.destroy()
            usingOnDeviceRecognizer = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            recognizer = (if (usingOnDeviceRecognizer) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }).also { speech ->
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        sendStatus(if (sessionActive) "POČÚVAM • KONVERZÁCIA AKTÍVNA" else "ČAKÁM NA HEJ JARVIS")
                    }
                    override fun onBeginningOfSpeech() { listening = true }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening = false }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onError(error: Int) {
                        listening = false
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Silence is normal, not a fault. Do not increase the delay
                                // after every quiet interval: that caused multi-second dead air.
                                consecutiveErrors = 0
                                scheduleRestart(300L)
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                consecutiveErrors++
                                recreateAndRestart(700L)
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                sendStatus("MIKROFÓN NEMÁ POVOLENIE")
                                stopSelf()
                            }
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                                if (usingOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    sendStatus("SLOVENSKÝ OFFLINE BALÍK NIE JE DOSTUPNÝ • SKÚŠAM SYSTÉMOVÉ ROZPOZNÁVANIE")
                                    createRecognizer(preferOnDevice = false)
                                    scheduleRestart(500L)
                                } else {
                                    consecutiveErrors++
                                    recreateAndRestart(1800L)
                                }
                            }
                            else -> {
                                consecutiveErrors = (consecutiveErrors + 1).coerceAtMost(5)
                                recreateAndRestart((900L + consecutiveErrors * 300L).coerceAtMost(2400L))
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false
                        consecutiveErrors = 0
                        val phrase = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty().trim()
                        processPhrase(phrase)
                        scheduleRestart(if (sessionActive) 250L else 350L)
                    }
                })
            }
        }.onFailure {
            sendStatus("CHYBA MIKROFÓNU: ${it.message ?: "neznáma chyba"}")
            scheduleRestart(1200L)
        }
    }

    private fun processPhrase(phrase: String) {
        val normalized = phrase.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return

        val wake = Regex("^(?:(?:hej|hey) )?jarvis\\b[ ,:]*", RegexOption.IGNORE_CASE)
        val match = wake.find(normalized)
        if (match != null) {
            val remainder = normalized.substring(match.range.last + 1).trim()
            sessionActive = true
            sendStatus("KONVERZÁCIA AKTÍVNA")
            if (remainder.isBlank()) sendCommandBroadcast(ACTION_WAKE, "")
            else dispatchCommand(remainder)
            return
        }
        if (sessionActive) dispatchCommand(phrase)
    }

    private fun dispatchCommand(command: String) {
        val normalized = command.lowercase(Locale.ROOT)
        val endConversation = listOf("vypni sa", "zatiaľ sa maj", "zatím sa maj", "maj sa", "končíme", "ukonči rozhovor", "ukonči konverzáciu", "dobrú noc")
        if (endConversation.any { normalized.contains(it) }) {
            sessionActive = false
            sendStatus("ČAKÁM NA HEJ JARVIS")
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

    private fun sendCommandBroadcast(action: String, command: String) {
        sendBroadcast(Intent(action).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun sendStatus(status: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, status))
    }

    private fun scheduleRestart(delay: Long) {
        if (stopped || pausedForSpeech || restartQueued) return
        restartQueued = true
        handler.postDelayed({
            restartQueued = false
            startRecognition()
        }, delay)
    }

    private fun startRecognition() {
        if (stopped || pausedForSpeech) return
        if (recognizer == null) {
            createRecognizer()
            scheduleRestart(250L)
            return
        }
        if (listening) return
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
            listening = true
        }.onFailure {
            listening = false
            recreateAndRestart(900L)
        }
    }

    private fun cancelRecognition() {
        handler.removeCallbacksAndMessages(null)
        restartQueued = false
        listening = false
        runCatching { recognizer?.cancel() }
    }

    private fun recreateAndRestart(delay: Long) {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
        if (!stopped && !pausedForSpeech) {
            handler.postDelayed({ createRecognizer(); scheduleRestart(200L) }, delay)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val openApp = PendingIntent.getActivity(
            this, 701,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 702,
            Intent(this, JarvisVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder
            .setContentTitle("JARVIS • Hlasový modul")
            .setContentText("Mikrofón je aktívny aj pri zhasnutej obrazovke. Povedzte „Hej Jarvis“.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Vypnúť mikrofón", stopService)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS hlasové ovládanie", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { cpuWakeLock?.let { if (it.isHeld) it.release() } }
        cpuWakeLock = null
        sendStatus("HLASOVÝ MODUL VYPNUTÝ")
        super.onDestroy()
    }

    /**
     * Removing the launcher task must not be treated as a request to stop
     * this explicitly enabled foreground voice service. Keep the recognizer
     * loop alive and recover it if the last recognition session ended.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!stopped && !pausedForSpeech) {
            runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
            scheduleRestart(350L)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var isRunning: Boolean = false
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
    }
}
