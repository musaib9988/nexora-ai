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
        const val NOTIFICATION_ID = 2026
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SEERU AI Hands-Free Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SEERU AI wake word listener active in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundListening() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexora AI Active")
            .setContentText("Hands-free listening active. Say 'Hey Nexora' or tap to talk.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
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

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val buffer = ShortArray(bufferSize)
            audioRecord?.startRecording()

            var consecutiveSpeechFrames = 0

            while (isListening) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = Math.sqrt(sum / read)

                    // Acoustic voice activity detection
                    if (rms > 6500) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) {
                            Log.d("SeeruService", "Voice wake activity detected!")
                            triggerWakeWordDetected()
                            consecutiveSpeechFrames = 0
                            delay(2500) // Debounce so multiple triggers don't collide
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                }
                delay(40)
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

        // Also launch activity if background allows
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_LISTEN", true)
        }
        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            // Background activity start restrictions on Android 10+
        }
    }

    override fun onDestroy() {
        stopForegroundListening()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
