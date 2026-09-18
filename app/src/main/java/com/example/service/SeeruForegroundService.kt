package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.Locale

class SeeruForegroundService : Service(), TextToSpeech.OnInitListener {
    private var isListening = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Handler & Speech Recognizer running on the Main Looper
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognizerActive = false
    private var lastTriggerTimestamp = 0L

    companion object {
        const val CHANNEL_ID = "seeru_assistant_channel"
        const val ALERT_CHANNEL_ID = "seeru_wake_alert_channel"
        const val NOTIFICATION_ID = 2026
        const val ALERT_NOTIFICATION_ID = 2027
        const val ACTION_START = "ACTION_START_SEERU"
        const val ACTION_STOP = "ACTION_STOP_SEERU"
        const val BROADCAST_WAKE_WORD = "com.example.seeru.WAKE_WORD_TRIGGERED"
        const val EXTRA_COMMAND = "EXTRA_COMMAND"

        /**
         * Global flag set by MainActivity / SpeechManager to pause the background
         * service's SpeechRecognizer while the user is actively interacting with the UI mic.
         */
        @Volatile
        var isForegroundAppListening: Boolean = false

        fun startService(context: Context) {
            val intent = Intent(context, SeeruForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("SeeruService", "Failed to start service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SeeruForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }

        fun requestIgnoreBatteryOptimization(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e("SeeruService", "Error initializing TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.ENGLISH)
            }
            isTtsReady = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundListening()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundListening()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keeps service alive when user swipes away app from Recent Tasks
        val prefs = getSharedPreferences("seeru_prefs", Context.MODE_PRIVATE)
        val isHandsFree = prefs.getBoolean("pref_hands_free_active", true)
        if (isHandsFree) {
            try {
                val restartIntent = Intent(applicationContext, SeeruForegroundService::class.java).apply {
                    action = ACTION_START
                }
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    2028,
                    restartIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e("SeeruService", "Error scheduling restart: ${e.message}")
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Ongoing background notification channel
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID,
                "Nexora Background Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nexora active for hands-free wake word detection"
                setShowBadge(false)
            }
            manager.createNotificationChannel(ongoingChannel)

            // 2. Wake-up alert channel
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Nexora Wake Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fired when wake phrase is recognized"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun startForegroundListening() {
        if (isListening) return
        isListening = true

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_LISTEN", true)
        }
        val pLaunchIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SeeruForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexora AI • Voice Assistant Active")
            .setContentText("Say \"Hey\" or \"Nexora\" to speak hands-free")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pLaunchIntent)
            .addAction(R.mipmap.ic_launcher, "🎙️ Talk Now", pLaunchIntent)
            .addAction(R.mipmap.ic_launcher, "Turn Off", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("SeeruService", "startForeground failed: ${e.message}")
        }

        // Acquire partial wake lock for reliability
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nexora::WakeWordLock").apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }
        } catch (e: Exception) {
            Log.e("SeeruService", "Failed to acquire wake lock: ${e.message}")
        }

        // Start continuous keyword spotting on Main Looper
        mainHandler.post {
            startKeywordRecognitionLoop()
        }
    }

    private fun startKeywordRecognitionLoop() {
        if (!isListening) return

        // If foreground UI is actively using mic, pause background recognizer and check back later
        if (isForegroundAppListening) {
            mainHandler.postDelayed({ startKeywordRecognitionLoop() }, 1000)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("SeeruService", "SpeechRecognizer not available")
            mainHandler.postDelayed({ startKeywordRecognitionLoop() }, 3000)
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isRecognizerActive = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isRecognizerActive = false
                    }

                    override fun onError(error: Int) {
                        isRecognizerActive = false
                        // Restart listening loop after brief delay
                        if (isListening) {
                            val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 500L
                            mainHandler.postDelayed({ startKeywordRecognitionLoop() }, delayMs)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isRecognizerActive = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                        val heard = matches.firstOrNull()?.trim()?.lowercase() ?: ""

                        if (heard.isNotBlank()) {
                            checkAndHandleWakeWord(heard)
                        }

                        if (isListening) {
                            mainHandler.postDelayed({ startKeywordRecognitionLoop() }, 400)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                        val heard = matches.firstOrNull()?.trim()?.lowercase() ?: ""
                        if (heard.isNotBlank()) {
                            checkAndHandleWakeWord(heard)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("SeeruService", "Error starting speech listener: ${e.message}")
            if (isListening) {
                mainHandler.postDelayed({ startKeywordRecognitionLoop() }, 1500)
            }
        }
    }

    /**
     * Inspects recognized speech to detect ACTUAL wake phrases ("hey", "nexora", "hey nexora", "suno", "namaste").
     * Ambient noise, breathing, or irrelevant talking will NEVER trigger this!
     */
    private fun checkAndHandleWakeWord(heardText: String) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < 3500L) {
            return // Debounce rapid multi-triggers
        }

        val wakeKeywords = listOf(
            "hey",
            "nexora",
            "hey nexora",
            "ok nexora",
            "suno",
            "namaste",
            "seeru",
            "hey seeru",
            "hello nexora"
        )

        // Check if any wake keyword is present in the recognized utterance
        val matchedKeyword = wakeKeywords.firstOrNull { keyword ->
            val regex = Regex("""\b$keyword\b""", RegexOption.IGNORE_CASE)
            regex.containsMatchIn(heardText)
        }

        if (matchedKeyword != null) {
            lastTriggerTimestamp = now
            triggerWakeWordDetected(heardText)
        }
    }

    private fun triggerWakeWordDetected(fullCommandText: String) {
        // 1. Haptic Feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 100), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 100), -1))
                } else {
                    v.vibrate(100)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 2. Audio Feedback (Natural confirmation)
        if (isTtsReady) {
            try {
                tts?.speak("Ji! Mai sun rahi hu.", TextToSpeech.QUEUE_FLUSH, null, "WAKE_ACK")
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 3. Broadcast intent for in-app or background listeners
        val broadcastIntent = Intent(BROADCAST_WAKE_WORD).apply {
            setPackage(packageName)
            putExtra(EXTRA_COMMAND, fullCommandText)
        }
        sendBroadcast(broadcastIntent)

        // 4. Launch or Bring MainActivity to front to show listening state
        try {
            val appIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_AUTO_LISTEN", true)
                putExtra("EXTRA_HEARD_COMMAND", fullCommandText)
            }
            startActivity(appIntent)
        } catch (e: Exception) {
            Log.e("SeeruService", "Could not start MainActivity: ${e.message}")
        }
    }

    private fun stopForegroundListening() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isRecognizerActive = false
        } catch (e: Exception) {
            // Ignore
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            // Ignore
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundListening()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            // Ignore
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
