package com.algo1127.batterychargeguess

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import kotlin.math.abs

object BatteryHardwareReader {

    fun getCurrentNowMa(context: Context): Int? {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentUA = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            if (currentUA != Integer.MIN_VALUE) {
                if (abs(currentUA) > 10000) currentUA / 1000 else currentUA
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Add this to your existing BatteryHardwareReader object
    @SuppressLint("PrivateApi")
    fun getDesignCapacityMah(context: Context): Int {
        return try {
            // Use reflection to access Android's hidden PowerProfile class
            val powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                .getConstructor(Context::class.java)
                .newInstance(context)

            val batteryCapacity = Class.forName("com.android.internal.os.PowerProfile")
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as Double

            batteryCapacity.toInt()
        } catch (e: Exception) {
            5000 // Fallback to 5000 if reflection fails on this specific phone
        }
    }

    // Keep your other methods (temp, voltage) if you want, but make sure
    // they use try/catch so they don't crash the app!
    fun getTemperatureCelsius(context: Context): Float? {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val temp = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // Just an example, temp is usually in intents
            null // We get temp from the Intent anyway, no need to read sysfs
        } catch (e: Exception) { null }
    }
}