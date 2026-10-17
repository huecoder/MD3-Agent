package com.mwagent.system.control

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

data class LaunchableApp(val label: String, val packageName: String, val activity: String) {
    val component: ComponentName get() = ComponentName(packageName, activity)
}

object InstalledApps {
    fun list(packages: PackageManager): List<LaunchableApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launcher, 0).map { info ->
            LaunchableApp(
                label = info.loadLabel(packages).toString(),
                packageName = info.activityInfo.packageName,
                activity = info.activityInfo.name,
            )
        }
    }

    fun find(packages: PackageManager, query: String): LaunchableApp? = best(list(packages), query)

    fun best(apps: List<LaunchableApp>, query: String): LaunchableApp? {
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) return null
        return apps.firstOrNull { it.label.lowercase() == wanted || it.packageName.lowercase() == wanted }
            ?: apps.filter { it.label.lowercase().startsWith(wanted) }.minByOrNull { it.label.length }
            ?: apps.filter { it.label.lowercase().contains(wanted) }.minByOrNull { it.label.length }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(wanted) }
    }
}
