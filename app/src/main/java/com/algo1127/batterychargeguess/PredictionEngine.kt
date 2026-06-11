package com.algo1127.batterychargeguess

import android.content.Context
import android.os.BatteryManager

class PredictionEngine(private val context: Context, private val tracker: ChargeTracker) {

    // Get real-time current from BatteryManager (in microamps)
    fun getCurrentMicroAmps(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)

        // The decompiled code had this exact conversion logic:
        return if (Math.abs(currentMicroAmps) > 10000) {
            currentMicroAmps / 1000 // Convert to milliamps
        } else {
            currentMicroAmps
        }
    }

    // Fallback prediction if we don't have historical data yet
    fun predictUsingCurrent(currentLevel: Int, batteryCapacityMah: Int): Long {
        val currentMa = Math.abs(getCurrentMicroAmps())
        if (currentMa == 0) return -1 // Can't calculate if current is 0

        val remainingMah = batteryCapacityMah - ((currentLevel / 100.0) * batteryCapacityMah)
        // (mAh / mA) * 60 = minutes
        return ((remainingMah / currentMa) * 60).toLong()
    }

    // The master function that chooses the best prediction
    fun getBestPrediction(currentLevel: Int, batteryCapacityMah: Int): Long {
        val historicalPrediction = tracker.predictTimeToFull(currentLevel)

        // If we have enough historical data, use the ML approach. Otherwise, use physics.
        return if (historicalPrediction != -1L) {
            historicalPrediction
        } else {
            predictUsingCurrent(currentLevel, batteryCapacityMah)
        }
    }
}