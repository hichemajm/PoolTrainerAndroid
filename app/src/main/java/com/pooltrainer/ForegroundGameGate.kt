package com.pooltrainer

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object ForegroundGameGate {
    const val GAME_PACKAGE = "com.miniclip.eightballpool"

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isEightBallPoolForeground(context: Context): Boolean {
        if (!hasUsageAccess(context)) return false
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(now - 2 * 60 * 1000L, now)
            val event = UsageEvents.Event()
            var latestTime = -1L
            var latestPackage: String? = null
            var latestIsForeground = false

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                val isForeground = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED)
                val isBackground = type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                    (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_PAUSED)
                if ((isForeground || isBackground) && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                    latestIsForeground = isForeground
                }
            }
            latestIsForeground && latestPackage == GAME_PACKAGE
        } catch (_: Exception) {
            false
        }
    }
}
