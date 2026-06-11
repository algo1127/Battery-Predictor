package com.algo1127.batterychargeguess

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppCategorizerSettings : AppCompatActivity() {

    private val categories = listOf("Games", "Video", "Camera", "Read", "Music", "Phone")
    private val appAssignments = mutableMapOf<String, MutableSet<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_categorizer_settings)

        loadAssignments()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val apps = getInstalledApps()

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = AppGridAdapter(apps, categories, appAssignments) { packageName, selectedCategories ->
            if (selectedCategories.isEmpty()) {
                appAssignments.remove(packageName)
            } else {
                appAssignments[packageName] = selectedCategories.toMutableSet()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveAssignments()
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = mutableListOf<AppInfo>()

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        for (ri in resolveInfos) {
            val packageName = ri.activityInfo.packageName
            if (packageName == this.packageName) continue

            val appName = ri.loadLabel(pm).toString()
            apps.add(AppInfo(appName, packageName))
        }

        return apps.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    private fun loadAssignments() {
        val prefs = getSharedPreferences("app_categories", Context.MODE_PRIVATE)
        appAssignments.clear()

        prefs.all.forEach { (packageName, value) ->
            if (value is Set<*>) {
                val categoriesSet = value.mapNotNull { it as? String }.toMutableSet()
                appAssignments[packageName] = categoriesSet
            }
        }
    }

    private fun saveAssignments() {
        val prefs = getSharedPreferences("app_categories", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()

        appAssignments.forEach { (packageName, categoriesSet) ->
            // Save as a Set so we can have multiple categories per app!
            editor.putStringSet(packageName, categoriesSet)
        }
        editor.apply()
    }
}

data class AppInfo(val name: String, val packageName: String)

class AppGridAdapter(
    private val apps: List<AppInfo>,
    private val categories: List<String>,
    private val assignments: Map<String, Set<String>>,
    private val onSelectionChanged: (String, Set<String>) -> Unit
) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position], categories, assignments[apps[position].packageName], onSelectionChanged)
    }

    override fun getItemCount() = apps.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)
        private val categoryContainer: ViewGroup = itemView.findViewById(R.id.categoryCheckboxes)

        fun bind(
            app: AppInfo,
            categories: List<String>,
            currentCategories: Set<String>?,
            onSelectionChanged: (String, Set<String>) -> Unit
        ) {
            nameView.text = app.name

            try {
                val pm = itemView.context.packageManager
                val icon: Drawable? = pm.getApplicationIcon(app.packageName)
                iconView.setImageDrawable(icon)
            } catch (e: Exception) {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            categoryContainer.removeAllViews()
            val selectedCategories = currentCategories?.toMutableSet() ?: mutableSetOf()

            categories.forEach { category ->
                val checkBox = CheckBox(itemView.context).apply {
                    text = category
                    isChecked = selectedCategories.contains(category)
                    textSize = 12f
                    setPadding(0, 4, 0, 4)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedCategories.add(category)
                        } else {
                            selectedCategories.remove(category)
                        }
                        onSelectionChanged(app.packageName, selectedCategories.toSet())
                    }
                }
                categoryContainer.addView(checkBox)
            }
        }
    }
}