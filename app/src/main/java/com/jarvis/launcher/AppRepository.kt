package com.jarvis.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isLeanback: Boolean,
    val banner: Drawable?,
    val icon: Drawable
) {
    fun newLaunchIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(if (isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
        .setClassName(packageName, activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("launch_counts", Context.MODE_PRIVATE)

    /** TV apps first, then regular (sideloaded phone) apps that have no TV entry. */
    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val seen = HashSet<String>()
        val apps = ArrayList<AppEntry>()

        val queries = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER to true,
            Intent.CATEGORY_LAUNCHER to false
        )
        for ((category, leanback) in queries) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val ai = info.activityInfo
                if (ai.packageName == context.packageName) continue
                if (!seen.add(ai.packageName)) continue

                // Many TV apps declare their banner on <application>, not on the activity.
                val banner = if (leanback) {
                    try {
                        ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                apps.add(
                    AppEntry(
                        label = info.loadLabel(pm).toString(),
                        packageName = ai.packageName,
                        activityName = ai.name,
                        isLeanback = leanback,
                        banner = banner,
                        icon = info.loadIcon(pm)
                    )
                )
            }
        }

        // Most-used apps float to the front; ties are alphabetical.
        return apps.sortedWith(
            compareByDescending<AppEntry> { launchCount(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }

    fun recordLaunch(packageName: String) {
        prefs.edit().putInt(packageName, launchCount(packageName) + 1).apply()
    }

    private fun launchCount(packageName: String): Int = prefs.getInt(packageName, 0)
}
