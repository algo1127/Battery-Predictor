package com.algo1127.batterychargeguess

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "BatteryPredictorChannel"
        const val NOTIFICATION_ID = 1
        const val PREFS_NAME = "battery_prediction"
        const val KEY_PREDICTION_FULL = "prediction_full"
        const val KEY_PREDICTION_EMPTY = "prediction_empty"
        const val KEY_CURRENT_LEVEL = "current_level"
        const val KEY_IS_CHARGING = "is_charging"
    }

    private lateinit var tracker: ChargeTracker
    private lateinit var profiler: SmartPowerProfiler
    private lateinit var healthCalculator: BatteryHealthCalculator

    private var lastLevel = -1
    private var lastTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val now = System.currentTimeMillis()
            val currentMa = BatteryHardwareReader.getCurrentNowMa(applicationContext) ?: 0

            if (lastLevel != -1) {
                if (isCharging && level == lastLevel + 1) {
                    val timeDelta = now - lastTime
                    tracker.addChargeSample(timeDelta)
                    updatePrediction(level, true)
                    lastTime = now

                } else if (!isCharging && level == lastLevel - 1) {
                    val timeDelta = now - lastTime
                    tracker.addDischargeSample(timeDelta)

                    // 🧠 SMART CATEGORY PROFILING
                    // 🧠 MULTI-LABEL CATEGORY PROFILING
                    processUsageCategories(lastTime, now, timeDelta)
                    // If category is null, the sample is ONLY in tracker.addDischargeSample()
                    // which feeds the general weighted average

                    updatePrediction(level, false)
                    lastTime = now
                }
            }

            if (lastLevel != level) {
                lastLevel = level
                lastTime = now
            }

            healthCalculator.onBatteryUpdate(level, currentMa, isCharging)
            saveStateToPrefs(level, isCharging)
        }
    }

    override fun onCreate() {
        super.onCreate()
        tracker = ChargeTracker(this)
        profiler = SmartPowerProfiler(this)
        healthCalculator = BatteryHealthCalculator(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var previousUsageSnapshot = mutableMapOf<String, Long>()

    private fun processUsageCategories(startTime: Long, endTime: Long, timeDelta: Long) {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)

        if (stats.isNullOrEmpty()) return

        val totalTime = endTime - startTime
        val categoriesProcessed = mutableSetOf<String>()
        val currentSnapshot = mutableMapOf<String, Long>()

        for (stat in stats) {
            if (stat.packageName == applicationContext.packageName) continue

            val packageName = stat.packageName
            val currentTotalTime = stat.totalTimeInForeground
            val previousTotalTime = previousUsageSnapshot[packageName] ?: 0L

            // Calculate the ACTUAL time used during this 1% window
            val deltaTime = currentTotalTime - previousTotalTime

            // Save for next comparison
            currentSnapshot[packageName] = currentTotalTime

            // Only process if the app was actually used during THIS window
            if (deltaTime > 0) {
                val share = deltaTime.toFloat() / totalTime

                if (share >= 0.15f) {
                    val category = AppCategorizer.categorize(applicationContext, packageName)

                    if (category != null && category != "Standby" && !categoriesProcessed.contains(category)) {
                        profiler.addSample(category, timeDelta)
                        categoriesProcessed.add(category)
                    }
                }
            }
        }

        // Update the snapshot for next time
        previousUsageSnapshot = currentSnapshot
    }

    private fun updatePrediction(level: Int, isCharging: Boolean) {
        val minsToFull = tracker.predictTimeToFull(level)
        val minsToEmpty = tracker.predictTimeToEmpty(level)

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PREDICTION_FULL, minsToFull)
            .putLong(KEY_PREDICTION_EMPTY, minsToEmpty)
            .apply()
    }

    private fun saveStateToPrefs(level: Int, isCharging: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CURRENT_LEVEL, level)
            .putBoolean(KEY_IS_CHARGING, isCharging)
            .putFloat("live_accumulated_mah", healthCalculator.getLiveAccumulatedMah().toFloat())
            .putInt("start_level", healthCalculator.getStartLevel())
            .putBoolean("is_health_tracking", healthCalculator.isTracking)
            .apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Battery Tracking", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Predictor Active")
            .setContentText("Learning your app usage patterns...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
}