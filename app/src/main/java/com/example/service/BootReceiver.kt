package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("seeru_prefs", Context.MODE_PRIVATE)
            val isHandsFree = prefs.getBoolean("pref_hands_free_active", true)
            if (isHandsFree) {
                SeeruForegroundService.startService(context)
            }
        }
    }
}
