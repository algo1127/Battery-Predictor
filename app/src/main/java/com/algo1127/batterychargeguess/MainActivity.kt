package com.algo1127.batterychargeguess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    // Battery Tracking UI
    private lateinit var tvLevel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPredictionFull: TextView
    private lateinit var tvPredictionEmpty: TextView
    private lateinit var btnToggleService: Button

    // Permission & Memory UI
    private lateinit var btnGrantUsage: Button
    private lateinit var btnShowMemory: Button

    // Category Predictor UI
    private lateinit var profiler: SmartPowerProfiler
    private lateinit var categoryGrid: GridLayout

    // Health UI
    private lateinit var tvDesignCapacity: TextView
    private lateinit var tvTrueCapacity: TextView
    private lateinit var tvHealthPercent: TextView
    private lateinit var tvLiveTracking: TextView
    private lateinit var btnResetHealth: Button
    private lateinit var healthCalculator: BatteryHealthCalculator

    // 🌊 Animated Background
    private lateinit var waveBackground: BatteryWaveView

    // 🔄 Pull-to-Refresh
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // 🆕 CHANGED: Fetch from hardware instead of hardcoding
    private var batteryCapacityMah by Delegates.notNull<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var isServiceRunning = false

    // ✅ Renamed "Utilities" to "Read", removed "Standby" (it's automatic)
    private val categories = listOf("Games", "Video", "Camera", "Read", "Music", "Phone")

    // 🔥 OPTIMIZATION: Smart update intervals
    private var updateInterval = 15_000L // Default: 15 seconds when screen is ON

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBatteryUI()
            updateCategoryGrid()
            updateHealthUI()
            // 🔥 Use dynamic updateInterval instead of hardcoded 1000
            handler.postDelayed(this, updateInterval)
        }
    }

    // 🔥 OPTIMIZATION: Screen state receiver
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateInterval = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> 15_000L  // 15 seconds when screen ON
                Intent.ACTION_SCREEN_OFF -> 90_000L // 90 seconds when screen OFF
                else -> updateInterval
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        batteryCapacityMah = BatteryHardwareReader.getDesignCapacityMah(this)
        tvDesignCapacity = findViewById(R.id.tvDesignCapacity)

        profiler = SmartPowerProfiler(this)
        healthCalculator = BatteryHealthCalculator(this)

        waveBackground = findViewById(R.id.waveBackground)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        tvLevel = findViewById(R.id.tvLevel)
        tvStatus = findViewById(R.id.tvStatus)
        tvPredictionFull = findViewById(R.id.tvPredictionFull)
        tvPredictionEmpty = findViewById(R.id.tvPredictionEmpty)
        btnToggleService = findViewById(R.id.btnToggleService)

        btnGrantUsage = findViewById(R.id.btnGrantUsage)
        btnShowMemory = findViewById(R.id.btnShowMemory)

        categoryGrid = findViewById(R.id.categoryGrid)

        tvTrueCapacity = findViewById(R.id.tvTrueCapacity)
        tvHealthPercent = findViewById(R.id.tvHealthPercent)
        tvLiveTracking = findViewById(R.id.tvLiveTracking)
        btnResetHealth = findViewById(R.id.btnResetHealth)

        updateCategoryGrid()

        btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, BatteryMonitorService::class.java))
                isServiceRunning = false
                btnToggleService.text = "Start Tracking"
                Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, BatteryMonitorService::class.java))
                isServiceRunning = true
                btnToggleService.text = "Stop Tracking"
                Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
            }
        }

        val btnCategorizeApps = findViewById<Button>(R.id.btnCategorizeApps)
        btnCategorizeApps.setOnClickListener {
            startActivity(Intent(this, AppCategorizerSettings::class.java))
        }

        btnGrantUsage.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'Battery Predictor' and toggle it ON", Toast.LENGTH_LONG).show()
        }

        btnShowMemory.setOnClickListener {
            showMemoryDialog()
        }

        btnResetHealth.setOnClickListener {
            healthCalculator.resetData()
            Toast.makeText(this, "Health data cleared. Plug in to recalibrate.", Toast.LENGTH_SHORT).show()
            updateHealthUI()
        }

        // 🔥 OPTIMIZATION: Pull-to-refresh setup
        swipeRefresh.setOnRefreshListener {
            refreshAllUI()
            swipeRefresh.isRefreshing = false
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
        }

        updateBatteryUI()
        updateCategoryGrid()
        updateHealthUI()
    }

    override fun onResume() {
        super.onResume()
        // Register screen state receiver
        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        // 🔥 OPTIMIZATION: Instant update when app comes to foreground
        refreshAllUI()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(screenStateReceiver)
        handler.removeCallbacks(updateRunnable)
    }

    // 🔥 Helper function for instant refresh
    private fun refreshAllUI() {
        updateBatteryUI()
        updateCategoryGrid()
        updateHealthUI()
    }

    private fun formatTime(minutes: Long): String {
        if (minutes < 0) return "--"
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h ${mins}m"
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "Soon"
        }
    }

    private fun updateBatteryUI() {
        val prefs = getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        val level = prefs.getInt(BatteryMonitorService.KEY_CURRENT_LEVEL, -1)
        val isCharging = prefs.getBoolean(BatteryMonitorService.KEY_IS_CHARGING, false)
        val minsToFull = prefs.getLong(BatteryMonitorService.KEY_PREDICTION_FULL, -1)
        val minsToEmpty = prefs.getLong(BatteryMonitorService.KEY_PREDICTION_EMPTY, -1)

        waveBackground.setLevel(level)

        if (level != -1) {
            tvLevel.text = "Battery: $level%"
            tvStatus.text = "Status: ${if (isCharging) "Charging ⚡" else "Discharging 🔋"}"

            if (isCharging) {
                tvPredictionEmpty.text = "Time to Empty: --"
                tvPredictionFull.text = if (minsToFull == -1L) {
                    "Time to Full: Learning..."
                } else {
                    "Time to Full: ${formatTime(minsToFull)}"
                }
            } else {
                tvPredictionFull.text = "Time to Full: --"
                tvPredictionEmpty.text = if (minsToEmpty == -1L) {
                    "Time to Empty: Learning..."
                } else {
                    "Time to Empty: ${formatTime(minsToEmpty)}"
                }
            }
        } else {
            tvLevel.text = "Battery: Waiting for data..."
            tvStatus.text = "Status: Unknown"
            tvPredictionFull.text = "Time to Full: --"
            tvPredictionEmpty.text = "Time to Empty: --"
        }
    }

    private fun updateCategoryGrid() {
        categoryGrid.removeAllViews()
        val prefs = getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        val currentLevel = prefs.getInt(BatteryMonitorService.KEY_CURRENT_LEVEL, 100)

        // 🔥 CRITICAL FIX: Force profiler to reload from disk
        profiler.reloadFromDisk()

        categories.forEach { category ->
            val result = profiler.getPrediction(category, batteryCapacityMah, currentLevel)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
                setBackgroundResource(R.drawable.corporate_card_bg)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = GridLayout.LayoutParams.MATCH_PARENT
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    setMargins(0, 4, 0, 4)
                }
            }

            val categoryName = TextView(this).apply {
                text = category
                textSize = 16f
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                    marginStart = 16
                }
            }

            val timeText = TextView(this).apply {
                text = formatTime(result.minutesRemaining)
                textSize = 16f
                setTextColor(Color.parseColor("#2563EB"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = 16
                }
            }

            val confidenceBadge = TextView(this).apply {
                text = "${result.confidence}%"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                setPadding(8, 4, 8, 4)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = 16
                }
            }

            val badgeColor = when {
                result.confidence > 70 -> Color.parseColor("#059669")
                result.confidence > 40 -> Color.parseColor("#D97706")
                else -> Color.parseColor("#DC2626")
            }
            confidenceBadge.setBackgroundColor(badgeColor)

            row.addView(categoryName)
            row.addView(timeText)
            row.addView(confidenceBadge)
            categoryGrid.addView(row)
        }
    }

    private fun updateHealthUI() {
        val prefs = getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        val isTracking = prefs.getBoolean("is_health_tracking", false)
        val liveMah = prefs.getFloat("live_accumulated_mah", 0f)
        val startLevel = prefs.getInt("start_level", -1)

        val trueCap = healthCalculator.getTrueCapacity()
        val healthPct = healthCalculator.getHealthPercentage()

        if (trueCap > 0) {
            tvTrueCapacity.text = "$trueCap mAh"
            tvHealthPercent.text = "${String.format("%.1f", healthPct)}%"

            if (trueCap < 4500) {
                tvHealthPercent.setTextColor(Color.parseColor("#DC2626"))
            } else {
                tvHealthPercent.setTextColor(Color.parseColor("#059669"))
            }
        } else {
            tvTrueCapacity.text = "Waiting..."
            tvHealthPercent.text = "--%"
        }

        // ✅ FIXED: Show actual design capacity
        tvDesignCapacity.text = "Design Capacity: $batteryCapacityMah mAh"

        if (isTracking) {
            tvLiveTracking.text = "+${String.format("%.0f", liveMah)} mAh (Started at $startLevel%)"
            tvLiveTracking.setTextColor(Color.parseColor("#2563EB"))
        } else {
            tvLiveTracking.text = "Idle"
            tvLiveTracking.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun showMemoryDialog() {
        val prefs1 = getSharedPreferences("category_stats", Context.MODE_PRIVATE)
        val categoryJson = prefs1.getString("category_history", "{}")

        val prefs2 = getSharedPreferences("battery_stats", Context.MODE_PRIVATE)
        val chargeJson = prefs2.getString("charging_history", "[]")
        val dischargeJson = prefs2.getString("discharging_history", "[]")

        val memoryLog = buildString {
            append("=== APP BRAIN MEMORY ===\n\n")
            append("📱 Category Learning:\n$categoryJson\n\n")
            append("🔋 Charge Samples (ms per 1%):\n$chargeJson\n\n")
            append("🪫 Discharge Samples (ms per 1%):\n$dischargeJson\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("App Memory (JSON)")
            .setMessage(memoryLog)
            .setPositiveButton("Clear Memory") { _, _ ->
                showClearOptionsDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showClearOptionsDialog() {
        val options = arrayOf(
            "📱 Discharge samples & Category learning",
            "🔋 Charging samples",
            "🗑️ Both (Clear everything)"
        )

        AlertDialog.Builder(this)
            .setTitle("What do you want to delete?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showConfirmationDialog("discharge_and_categories")
                    1 -> showConfirmationDialog("charging")
                    2 -> showConfirmationDialog("everything")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmationDialog(type: String) {
        val message = when (type) {
            "discharge_and_categories" -> "Delete discharge samples and category learning?\n\nThis will reset all app usage predictions and category data."
            "charging" -> "Delete charging samples?\n\nThis will reset time-to-full predictions."
            "everything" -> "Delete ALL memory data?\n\nThis will reset everything: predictions, categories, and battery health calculations."
            else -> ""
        }

        AlertDialog.Builder(this)
            .setTitle("⚠️ Are you sure?")
            .setMessage(message)
            .setPositiveButton("Yes, delete") { _, _ ->
                executeClear(type)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeClear(type: String) {
        val prefs1 = getSharedPreferences("category_stats", Context.MODE_PRIVATE)
        val prefs2 = getSharedPreferences("battery_stats", Context.MODE_PRIVATE)

        when (type) {
            "discharge_and_categories" -> {
                prefs1.edit().clear().apply()
                val chargeJson = prefs2.getString("charging_history", "[]")
                prefs2.edit().clear().apply()
                prefs2.edit().putString("charging_history", chargeJson).apply()
                Toast.makeText(this, "Discharge samples & categories cleared!", Toast.LENGTH_SHORT).show()
            }
            "charging" -> {
                val dischargeJson = prefs2.getString("discharging_history", "[]")
                prefs2.edit().clear().apply()
                prefs2.edit().putString("discharging_history", dischargeJson).apply()
                Toast.makeText(this, "Charging samples cleared!", Toast.LENGTH_SHORT).show()
            }
            "everything" -> {
                prefs1.edit().clear().apply()
                prefs2.edit().clear().apply()
                Toast.makeText(this, "All memory wiped! App is amnesiac now.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}