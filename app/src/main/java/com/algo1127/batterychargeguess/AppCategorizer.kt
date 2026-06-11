package com.algo1127.batterychargeguess

import android.content.Context
import android.util.Log

object AppCategorizer {

    fun categorize(context: Context, packageName: String): String? {
        val prefs = context.getSharedPreferences("app_categories", Context.MODE_PRIVATE)

        // Read the Set of categories
        val manualCategories = prefs.getStringSet(packageName, emptySet())

        Log.d("AppCategorizer", "Package: $packageName, Categories: $manualCategories")

        return if (manualCategories.isNullOrEmpty()) {
            Log.d("AppCategorizer", "→ Returning null (unclassified)")
            null
        } else {
            val first = manualCategories.first()
            Log.d("AppCategorizer", "→ Returning: $first")
            first
        }
    }
}