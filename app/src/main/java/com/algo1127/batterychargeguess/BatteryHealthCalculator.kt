package com.algo1127.batterychargeguess

import android.content.Context
import android.util.Log

class BatteryHealthCalculator(private val context: Context) {

    private val TAG = "HealthCalculator"
    private val prefs = context.getSharedPreferences("battery_health", Context.MODE_PRIVATE)
    private val designCapacityMah = BatteryHardwareReader.getDesignCapacityMah(context)

    var isTracking = false
        private set
    private var startLevel = -1
    private var accumulatedMah = 0.0f
    private var lastUpdateTime = 0L

    init {
        loadState()
    }

    fun onBatteryUpdate(level: Int, currentMa: Int, isCharging: Boolean) {
        val now = System.currentTimeMillis()

        // 1. START TRACKING
        if (!isTracking && isCharging && level < 95) {
            Log.d(TAG, "🟢 Starting tracking at $level%")
            isTracking = true
            startLevel = level
            accumulatedMah = 0f
            lastUpdateTime = now
            saveState()
        }
        // 2. ACCUMULATE
        else if (isTracking && isCharging) {
            if (lastUpdateTime > 0) {
                val timeDeltaHours = (now - lastUpdateTime) / 3600000f
                val mahAdded = kotlin.math.abs(currentMa) * timeDeltaHours
                accumulatedMah += mahAdded

                // Log every accumulation event
                Log.d(TAG, "⚡ Accumulated $mahAdded mAh. Total: $accumulatedMah mAh. Instant Current: $currentMa mA")
            }
            lastUpdateTime = now
            saveState()

            // 🎯 TRIGGER CALCULATION AT 100%
            if (level >= 100) {
                Log.d(TAG, "🎉 Hit 100%! Calculating health now.")
                calculateAndSaveHealth(level)
                isTracking = false
                startLevel = -1
                accumulatedMah = 0f
                saveState()
            }
        }
        // 3. STOP & CALCULATE ON UNPLUG
        else if (isTracking && !isCharging) {
            Log.d(TAG, "🔌 Unplugged before 100%! Calculating health.")
            calculateAndSaveHealth(level)
            isTracking = false
            startLevel = -1
            accumulatedMah = 0f
            saveState()
        }
    }

    private fun calculateAndSaveHealth(endLevel: Int) {
        if (startLevel == -1 || endLevel <= startLevel) {
            Log.e(TAG, "❌ Failed math: start=$startLevel, end=$endLevel")
            return
        }

        val percentGained = endLevel - startLevel
        val trueCapacity = (accumulatedMah / percentGained) * 100f
        val healthPercent = (trueCapacity / designCapacityMah) * 100f

        Log.d(TAG, "📊 CALCULATION:")
        Log.d(TAG, "Start Level: $startLevel% -> End Level: $endLevel%")
        Log.d(TAG, "Percent Gained: $percentGained%")
        Log.d(TAG, "Total mAh Added: $accumulatedMah")
        Log.d(TAG, "✅ True Capacity: $trueCapacity mAh | Health: $healthPercent%")

        prefs.edit()
            .putFloat("true_capacity", trueCapacity)
            .putFloat("health_percent", healthPercent)
            .apply()
    }

    private fun saveState() {
        prefs.edit()
            .putBoolean("is_tracking", isTracking)
            .putInt("start_level", startLevel)
            .putFloat("live_accumulated_mah", accumulatedMah)
            .putLong("last_update_time", lastUpdateTime)
            .apply()
    }

    private fun loadState() {
        isTracking = prefs.getBoolean("is_tracking", false)
        startLevel = prefs.getInt("start_level", -1)
        accumulatedMah = prefs.getFloat("live_accumulated_mah", 0f)
        lastUpdateTime = prefs.getLong("last_update_time", 0L)
    }

    fun resetData() {
        prefs.edit().clear().apply()
        isTracking = false
        startLevel = -1
        accumulatedMah = 0f
    }

    fun getTrueCapacity(): Int = prefs.getFloat("true_capacity", 0f).toInt()
    fun getHealthPercentage(): Float = prefs.getFloat("health_percent", 0f)
    fun getLiveAccumulatedMah(): Float = accumulatedMah
    fun getStartLevel(): Int = startLevel
}