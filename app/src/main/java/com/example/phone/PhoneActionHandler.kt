package com.example.phone

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

class PhoneActionHandler(private val context: Context) {

    fun dialContact(phoneNumberOrName: String) {
        val sanitized = phoneNumberOrName.filter { it.isDigit() || it == '+' }
        val uri = if (sanitized.isNotBlank()) {
            Uri.parse("tel:$sanitized")
        } else {
            Uri.parse("tel:")
        }

        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun composeSms(recipient: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$recipient")
            putExtra("sms_body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general camera app
            val fallback = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    fun controlMedia(command: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val keyCode = when (command.uppercase()) {
            "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAlarms() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Clock app might not be available
        }
    }

    fun openApp(appName: String): Boolean {
        val pm = context.packageManager
        val lower = appName.lowercase().trim()

        if (lower.contains("setting") || lower.contains("settings")) {
            openSettings()
            return true
        }
        if (lower.contains("clock") || lower.contains("alarm")) {
            openAlarms()
            return true
        }
        if (lower.contains("camera")) {
            openCamera()
            return true
        }

        // Common known package aliases
        val targetPackage = when {
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("chrome") || lower.contains("browser") -> "com.android.chrome"
            lower.contains("map") -> "com.google.android.apps.maps"
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("gmail") || lower.contains("mail") -> "com.google.android.gm"
            lower.contains("spotify") -> "com.spotify.music"
            else -> null
        }

        if (targetPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                return true
            }
        }

        // Search installed applications by display label
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.equals(lower, ignoreCase = true) || label.contains(lower) || lower.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Package lookup fallback
        }

        return false
    }
}
