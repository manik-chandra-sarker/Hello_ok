package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.SettingsPreferences
import com.example.service.VoiceCaptureService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val settings = SettingsPreferences.getInstance(context).getSettings()
            if (settings.startOnBoot) {
                Log.d("BootReceiver", "Starting VoiceCaptureService on boot as requested by user")
                VoiceCaptureService.startService(context)
            }
        }
    }
}
