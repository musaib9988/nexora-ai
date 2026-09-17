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

    fun composeSms(recipientPhoneOrName: String, body: String): Boolean {
        val digits = recipientPhoneOrName.filter { it.isDigit() || it == '+' }
        val uri = if (digits.isNotBlank()) Uri.parse("smsto:$digits") else Uri.parse("smsto:")

        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to general messaging app
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android-dir/mms-sms"
                putExtra("sms_body", body)
                putExtra(Intent.EXTRA_TEXT, body)
                if (digits.isNotBlank()) putExtra("address", digits)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallback)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun openCamera(): Boolean {
        // 1. Primary standard intent to launch the device camera viewfinder
        val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(stillIntent)
            return true
        } catch (e: Exception) {
            // Fallback
        }

        // 2. Action image capture intent
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(captureIntent)
            return true
        } catch (e: Exception) {
            // Fallback
        }

        // 3. Look for camera package in installed packages
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            for (app in installedApps) {
                val pkg = app.packageName.lowercase()
                if (pkg.contains("camera")) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return false
    }

    fun controlMedia(command: String, songQuery: String = "") {
        val upper = command.uppercase()

        if (upper == "PLAY") {
            // 1. Play built-in soothing melody so user immediately hears music
            BuiltInMusicPlayer.play()

            // 2. If a specific song or artist was mentioned, also try to open music search/YouTube
            if (songQuery.isNotBlank()) {
                val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, songQuery)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(searchIntent)
                } catch (e: Exception) {
                    // Fallback to YouTube web/app
                    val ytIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(songQuery)}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(ytIntent)
                    } catch (ex: Exception) {
                        // Ignore
                    }
                }
            }
        } else {
            // PAUSE or STOP
            BuiltInMusicPlayer.stop()
        }

        // 3. Also dispatch system media key events for any background music players
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val keyCode = when (upper) {
            "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
        } catch (e: Exception) {
            // Ignore
        }
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
