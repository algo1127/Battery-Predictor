package com.algo1127.batterychargeguess

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class ChargeTracker(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("battery_stats", Context.MODE_PRIVATE)
    private val MAX_SAMPLES = 120

    private var chargingSamples: MutableList<Long> = mutableListOf()
    private var dischargingSamples: MutableList<Long> = mutableListOf()

    init {
        loadSamples()
    }

    // --- CHARGING LOGIC ---
    fun addChargeSample(timeDeltaMs: Long) {
        if (timeDeltaMs > 0 && timeDeltaMs < 1_000_000) {
            chargingSamples.add(timeDeltaMs)
            if (chargingSamples.size > MAX_SAMPLES) chargingSamples.removeAt(0)
            saveSamples()
        }
    }

    fun predictTimeToFull(currentLevel: Int): Long {
        if (chargingSamples.size < 2) return -1
        val avg = chargingSamples.filter { it <= 1_000_000 }.average().toLong()
        return (avg * (100 - currentLevel)) / 60_000
    }

    // --- DISCHARGING LOGIC ---
    fun addDischargeSample(timeDeltaMs: Long) {
        // 🛠️ FIX 1: Lowered threshold from 10 minutes (600k) to 30 seconds (30k).
        // Now it properly records gaming, video, and active usage!
        if (timeDeltaMs > 30_000 && timeDeltaMs < 86_400_000) {
            dischargingSamples.add(timeDeltaMs)
            if (dischargingSamples.size > MAX_SAMPLES) dischargingSamples.removeAt(0)
            saveSamples()
        }
    }

    fun predictTimeToEmpty(currentLevel: Int): Long {
        // 🛠️ FIX 2: Removed the early return. We ALWAYS try to calculate using categories first.

        // Try category-based weighted average first
        val profiler = SmartPowerProfiler(context)
        val categoryUsage = profiler.getUsageDistribution()

        if (categoryUsage.isNotEmpty()) {
            var weightedDrainRate = 0.0
            var totalWeight = 0.0

            categoryUsage.forEach { (category, percentage) ->
                val categoryDrainRate = profiler.getAverageDrainRate(category)
                if (categoryDrainRate > 0) {
                    weightedDrainRate += categoryDrainRate * percentage
                    totalWeight += percentage
                }
            }

            if (totalWeight > 0) {
                val avgMsPerPercent = weightedDrainRate / totalWeight
                // 🛠️ FIX 3: DIVIDE BY 60,000 to convert milliseconds to minutes!
                return ((avgMsPerPercent * currentLevel) / 60_000).toLong()
            }
        }

        // Fallback: Use general discharge samples
        if (dischargingSamples.isNotEmpty()) {
            val avg = dischargingSamples.average()
            // 🛠️ FIX 3: DIVIDE BY 60,000 to convert milliseconds to minutes!
            return ((avg * currentLevel) / 60_000).toLong()
        }

        // If absolutely no data exists anywhere
        return -1L
    }

    // --- PERSISTENCE ---
    private fun saveSamples() {
        val chargeJson = JSONArray().apply { chargingSamples.forEach { put(it) } }
        val dischargeJson = JSONArray().apply { dischargingSamples.forEach { put(it) } }

        prefs.edit()
            .putString("charging_history", chargeJson.toString())
            .putString("discharging_history", dischargeJson.toString())
            .apply()
    }

    private fun loadSamples() {
        val chargeString = prefs.getString("charging_history", "[]")
        val dischargeString = prefs.getString("discharging_history", "[]")

        val chargeJson = JSONArray(chargeString)
        val dischargeJson = JSONArray(dischargeString)

        chargingSamples.clear()
        dischargingSamples.clear()

        for (i in 0 until chargeJson.length()) chargingSamples.add(chargeJson.getLong(i))
        for (i in 0 until dischargeJson.length()) dischargingSamples.add(dischargeJson.getLong(i))
    }
}