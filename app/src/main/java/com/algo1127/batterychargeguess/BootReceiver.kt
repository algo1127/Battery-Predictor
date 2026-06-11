package com.algo1127.batterychargeguess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Listen for standard boot and quick boot (common on MediaTek/Umidigi devices)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "Device booted! Waking up Battery Predictor...")

            val serviceIntent = Intent(context, BatteryMonitorService::class.java)

            // Start as a foreground service so the OS doesn't kill it immediately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}