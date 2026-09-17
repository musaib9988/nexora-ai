package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*

class SeeruForegroundService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var isListening = false
    private var audioRecord: AudioRecord? = null

    companion object {
        const val CHANNEL_ID = "seeru_assistant_channel"
        const val ALERT_CHANNEL_ID = "seeru_wake_alert_channel"
        const val NOTIFICATION_ID = 2026
        const val ALERT_NOTIFICATION_ID = 2027
        const val ACTION_START = "ACTION_START_SEERU"
        const val ACTION_STOP = "ACTION_STOP_SEERU"
        const val BROADCAST_WAKE_WORD = "com.example.seeru.WAKE_WORD_TRIGGERED"

        fun startService(context: Context) {
            val intent = Intent(context, SeeruForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SeeruForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Ongoing status channel
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seeru AI Hands-Free Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SEERU AI wake word listener active in the background"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)

            // High priority alert channel for when "Hey Seeru" is heard
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Seeru AI Wake Word Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when 'Hey Seeru' is detected in the background"
                enableVibration(true)
            }
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun startForegroundListening() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_AUTO_LISTEN", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seeru AI Active")
            .setContentText("Hands-free active. Say 'Hey Seeru' or tap to speak.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Tap to Talk",
                pendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isListening) {
            isListening = true
            serviceScope.launch {
                runAcousticWakeDetector()
            }
        }
    }

    private fun stopForegroundListening() {
        isListening = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun runAcousticWakeDetector() = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize <= 0) return@withContext

        // Try standard MIC first, fallback to VOICE_RECOGNITION
        val audioSources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        var recordCreated = false
        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize.coerceAtLeast(4096)
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    recordCreated = true
                    break
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                // Try next audio source
            }
        }

        if (!recordCreated || audioRecord == null) {
            Log.e("SeeruService", "Could not initialize AudioRecord with any audio source")
            return@withContext
        }

        try {
            val buffer = ShortArray(bufferSize.coerceAtLeast(2048))
            audioRecord?.startRecording()

            var consecutiveSpeechFrames = 0
            val prefs = getSharedPreferences("seeru_prefs", Context.MODE_PRIVATE)

            while (isListening) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = Math.sqrt(sum / read)

                    // Calculate sensitivity threshold:
                    // sensitivity ranges 0.1 to 1.0 (default 0.7)
                    // threshold ranges 2800 (low sensitivity) down to 1000 (high sensitivity)
                    val sensitivity = prefs.getFloat("pref_wake_sensitivity", 0.7f).coerceIn(0.1f, 1.0f)
                    val threshold = 2800.0 - (sensitivity * 1800.0)

                    if (rms > threshold) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) {
                            Log.d("SeeruService", "Voice wake activity detected (RMS: $rms > $threshold)")
                            triggerWakeWordDetected()
                            consecutiveSpeechFrames = 0
                            delay(3000) // Debounce so multiple triggers don't collide
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                }
                delay(30)
            }
        } catch (e: SecurityException) {
            Log.e("SeeruService", "Microphone permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("SeeruService", "AudioRecord error: ${e.message}")
        }
    }

    private fun triggerWakeWordDetected() {
        val broadcastIntent = Intent(BROADCAST_WAKE_WORD)
        sendBroadcast(broadcastIntent)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("EXTRA_AUTO_LISTEN", true)
        }

        // Show Heads-Up Alert Notification for Android 10+ background activity policy
        val alertPendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("🎙️ 'Hey Seeru' Detected!")
            .setContentText("Listening for your command... Tap to speak.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(alertPendingIntent)
            .setFullScreenIntent(alertPendingIntent, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(ALERT_NOTIFICATION_ID, alertNotification)

        // Try direct launch if activity allows
        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            // Handled via Heads-up notification
        }
    }

    override fun onDestroy() {
        stopForegroundListening()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
