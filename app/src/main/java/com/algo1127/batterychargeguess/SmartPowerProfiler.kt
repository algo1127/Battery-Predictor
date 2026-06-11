package com.algo1127.batterychargeguess

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class PredictionResult(
    val minutesRemaining: Long,
    val confidence: Int,
    val sampleCount: Int,
    val isLearned: Boolean
)

class SmartPowerProfiler(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("category_stats", Context.MODE_PRIVATE)

    // 🔥 The Spyware's Hardcoded Fallback Values (in mA)
    private val baselinePowerMa = mapOf(
        "Games" to 870,
        "Video" to 470,
        "Camera" to 920,
        "Read" to 410,
        "Music" to 220,
        "Phone" to 210,
        "Standby" to 25 // Spyware used 25mA for standby in Batterys.java
    )

    // Stores actual measured time (in milliseconds) it takes to drop 1% for each category
    private var learnedSamples: MutableMap<String, MutableList<Long>> = mutableMapOf()

    init {
        loadSamples()
    }

    // Call this when the battery drops 1% while using a specific category
    fun addSample(category: String, timePerPercentMs: Long) {
        val list = learnedSamples.getOrPut(category) { mutableListOf() }

        // Filter out garbage data (must be between 30 seconds and 24 hours per 1%)
        if (timePerPercentMs in 30_000..86_400_000) {
            list.add(timePerPercentMs)
            if (list.size > 50) list.removeAt(0) // Keep last 50 samples per category
            saveSamples()
        }
    }

    // The Master Prediction Function
    fun getPrediction(category: String, batteryCapacityMah: Int, currentLevel: Int): PredictionResult {
        val samples = learnedSamples[category] ?: emptyList()
        val sampleCount = samples.size
        val confidence = calculateConfidence(sampleCount)

        val timePerPercentMs: Long = if (sampleCount >= 5 && confidence > 20) {
            // 🧠 SMART MODE: Use actual measured data
            samples.average().toLong()
        } else {
            // 🕵️ FALLBACK MODE: Use spyware's hardcoded physics math
            // Formula: (Capacity mAh / Drain mA) = Hours for 100%
            // Convert to milliseconds per 1%
            val drainMa = baselinePowerMa[category] ?: 500
            val hoursFor100 = batteryCapacityMah.toFloat() / drainMa
            val minutesFor100 = hoursFor100 * 60
            val minutesPer1Percent = minutesFor100 / 100
            (minutesPer1Percent * 60_000).toLong()
        }

        val totalMinutesLeft = (timePerPercentMs * currentLevel) / 60_000
        val isLearned = sampleCount >= 5

        return PredictionResult(totalMinutesLeft, confidence, sampleCount, isLearned)
    }

    fun getUsageDistribution(): Map<String, Double> {
        val prefs = context.getSharedPreferences("category_stats", Context.MODE_PRIVATE)
        val categoryJson = prefs.getString("category_history", "{}") ?: "{}"

        return try {
            val json = org.json.JSONObject(categoryJson)
            val categoryCounts = mutableMapOf<String, Int>()
            var totalCount = 0

            // Count samples per category
            json.keys().forEach { category ->
                val samples = json.getJSONArray(category)
                val count = samples.length()
                categoryCounts[category] = count
                totalCount += count
            }

            // Calculate percentages
            if (totalCount > 0) {
                categoryCounts.mapValues { (_, count) ->
                    count.toDouble() / totalCount
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getAverageDrainRate(category: String): Double {
        val prefs = context.getSharedPreferences("category_stats", Context.MODE_PRIVATE)
        val categoryJson = prefs.getString("category_history", "{}") ?: "{}"

        return try {
            val json = org.json.JSONObject(categoryJson)
            val samples = json.getJSONArray(category)

            if (samples.length() > 0) {
                var sum = 0L
                for (i in 0 until samples.length()) {
                    sum += samples.getLong(i)
                }
                sum.toDouble() / samples.length()
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateConfidence(sampleCount: Int): Int {
        // 5 samples = ~20% confidence. 40 samples = 90% confidence. Capped at 95%.
        return minOf(95, (sampleCount * 2) + 10)
    }

    // --- DATA PERSISTENCE ---
    private fun saveSamples() {
        val json = JSONObject()
        learnedSamples.forEach { (category, list) ->
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it) }
            json.put(category, jsonArray)
        }
        prefs.edit().putString("category_history", json.toString()).apply()
    }

    private fun loadSamples() {
        val jsonString = prefs.getString("category_history", "{}")
        val json = JSONObject(jsonString)
        learnedSamples.clear()

        json.keys().forEach { category ->
            val jsonArray = json.getJSONArray(category)
            val list = mutableListOf<Long>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getLong(i))
            }
            learnedSamples[category] = list
        }
    }

    fun reloadFromDisk() {
        // Clear existing data
        learnedSamples.clear()

        // Re-read from SharedPreferences
        val prefs = context.getSharedPreferences("category_stats", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("category_history", "{}")
        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                json.keys().forEach { key ->
                    val samples = json.getJSONArray(key).let { arr ->
                        (0 until arr.length()).map { arr.getLong(it) }
                    }
                    learnedSamples[key] = samples.toMutableList()
                }
            } catch (e: Exception) {
                Log.e("Profiler", "Error reloading data", e)
            }
        }
    }
}