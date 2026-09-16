package com.Pinak.ProductiveClub.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageMinutes: Int,
    val percentage: Float,
    val color: Color
)

data class ScreenTimeBreakdown(
    val totalMinutes: Int,
    val topApps: List<AppUsageInfo>
)

/**
 * Handles time calculations from midnight to current time,
 * duration clamping, and human-readable formatting.
 */
object TimeWindowHelper {

    /**
     * Computes the exact timestamp of today's start (00:00:00.000) in the device's local timezone.
     */
    fun getStartOfTodayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * Returns the current system timestamp in milliseconds.
     */
    fun getCurrentMillis(): Long = System.currentTimeMillis()

    /**
     * Returns the pair of (startOfToday, currentTime).
     */
    fun getTodayWindow(): Pair<Long, Long> {
        val start = getStartOfTodayMillis()
        val now = getCurrentMillis()
        return Pair(start, now)
    }

    /**
     * Maximum possible elapsed milliseconds since midnight today.
     */
    fun getMaxPossibleElapsedToday(): Long {
        val (start, now) = getTodayWindow()
        return (now - start).coerceAtLeast(1L)
    }

    /**
     * Clamps any measured duration so that no app can report more milliseconds
     * than total elapsed time since midnight.
     */
    fun clampDuration(durationMillis: Long): Long {
        return durationMillis.coerceIn(0L, getMaxPossibleElapsedToday())
    }

    /**
     * Formats total minutes into clean, human-readable text (e.g., "1h 45m" or "32m").
     */
    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
}

/**
 * Production-Grade Screen Time & Foreground App Usage Helper
 *
 * Uses UsageStatsManager.queryUsageStats(INTERVAL_DAILY, startOfDay, now) directly
 * to retrieve OS-aggregated totalTimeInForeground per package for today.
 */
object ScreenTimeHelper {

    private val PALETTE = listOf(
        Color(0xFF43A047), // Green
        Color(0xFF1E88E5), // Blue
        Color(0xFFFB8C00), // Orange
        Color(0xFFE53935), // Red
        Color(0xFF8E24AA), // Purple
        Color(0xFF78909C)  // Slate (Others)
    )

    // In-memory cache to prevent continuous polling fatigue and IPC throttling
    @Volatile
    private var cachedBreakdown: ScreenTimeBreakdown? = null

    @Volatile
    private var lastQueryTimestamp: Long = 0L

    private const val THROTTLE_WINDOW_MS = 3_000L // 3-second cache window

    /**
     * Runtime AppOps permission check
     */
    fun hasUsagePermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
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
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Direct Boot User Unlocked check
     */
    fun isUserUnlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            userManager?.isUserUnlocked ?: true
        } else {
            true
        }
    }

    fun getTodayScreenTimeMinutes(context: Context, forceRefresh: Boolean = false): Int {
        return getTodayScreenTimeBreakdown(context, forceRefresh).totalMinutes
    }

    /**
     * Synchronous breakdown retrieval with in-memory throttling cache
     */
    fun getTodayScreenTimeBreakdown(context: Context, forceRefresh: Boolean = false): ScreenTimeBreakdown {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedBreakdown != null && (now - lastQueryTimestamp < THROTTLE_WINDOW_MS)) {
            return cachedBreakdown!!
        }

        synchronized(this) {
            if (!forceRefresh && cachedBreakdown != null && (now - lastQueryTimestamp < THROTTLE_WINDOW_MS)) {
                return cachedBreakdown!!
            }
            val result = computeTodayScreenTimeBreakdown(context)
            cachedBreakdown = result
            lastQueryTimestamp = System.currentTimeMillis()
            return result
        }
    }

    /**
     * Asynchronous Coroutine Execution to prevent main-thread IPC blocking
     */
    suspend fun getTodayScreenTimeBreakdownAsync(
        context: Context,
        forceRefresh: Boolean = false
    ): ScreenTimeBreakdown = withContext(Dispatchers.IO) {
        getTodayScreenTimeBreakdown(context, forceRefresh)
    }

    /**
     * Core computation implementation using real-time UsageEvents state-machine parsing.
     * Accurately captures ongoing sessions, all user games/apps, intra-app activity transitions,
     * lock/unlock cycles, and midnight window crossing.
     */
    private fun computeTodayScreenTimeBreakdown(context: Context): ScreenTimeBreakdown {
        if (!hasUsagePermission(context) || !isUserUnlocked(context)) {
            return ScreenTimeBreakdown(0, emptyList())
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return ScreenTimeBreakdown(0, emptyList())

            val pm = context.packageManager
            val (startOfDay, now) = TimeWindowHelper.getTodayWindow()

            // Discover Home Launchers dynamically
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val launcherPackages = try {
                pm.queryIntentActivities(launcherIntent, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
            } catch (_: Exception) {
                emptySet()
            }

            // Discover Input Method Editors (Keyboards) dynamically
            val imePackages = try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }

            val systemPlumbingExcluded = setOf(
                "com.android.systemui",
                "android",
                "com.google.android.permissioncontroller",
                "com.android.keyguard"
            )

            fun isUserFacingApp(pkg: String): Boolean {
                if (pkg == context.packageName) return true
                if (pkg in launcherPackages || pkg in imePackages || pkg in systemPlumbingExcluded) return false

                return try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }

                    // Check if it's categorized as a game
                    val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appInfo.category == ApplicationInfo.CATEGORY_GAME
                    } else {
                        @Suppress("DEPRECATION")
                        (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    }
                    if (isGame) return true

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystem) {
                        // System apps: Include if user-launchable or Settings
                        pm.getLaunchIntentForPackage(pkg) != null || pkg == "com.android.settings"
                    } else {
                        // All user-installed apps and games: Always include
                        true
                    }
                } catch (_: Exception) {
                    // Fallback to true so missing metadata never drops a user app/game
                    true
                }
            }

            val perAppMillis = mutableMapOf<String, Long>()

            // Query UsageEvents starting 12 hours before midnight to accurately bridge any session across 00:00
            val queryStartTime = startOfDay - (12 * 60 * 60 * 1000L)
            val usageEvents = try {
                usageStatsManager.queryEvents(queryStartTime, now)
            } catch (_: Exception) {
                null
            }

            if (usageEvents != null && usageEvents.hasNextEvent()) {
                val resumedActivities = mutableMapOf<String, MutableSet<String>>()
                var currentForegroundPkg: String? = null
                var currentSessionStart: Long = 0L

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                var isScreenInteractive = powerManager?.isInteractive ?: true

                fun addSessionDuration(pkg: String, sessionStart: Long, sessionEnd: Long) {
                    if (sessionStart <= 0L || sessionEnd <= sessionStart) return
                    val effectiveStart = maxOf(sessionStart, startOfDay)
                    val effectiveEnd = minOf(sessionEnd, now)
                    if (effectiveEnd > effectiveStart) {
                        val duration = effectiveEnd - effectiveStart
                        perAppMillis[pkg] = (perAppMillis[pkg] ?: 0L) + duration
                    }
                }

                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val eventTime = event.timeStamp
                    val eventType = event.eventType
                    val className = event.className ?: ""

                    when (eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            if (!isScreenInteractive) {
                                isScreenInteractive = true
                            }
                            val classes = resumedActivities.getOrPut(pkg) { mutableSetOf() }
                            classes.add(className)

                            if (currentForegroundPkg != pkg) {
                                if (currentForegroundPkg != null) {
                                    addSessionDuration(currentForegroundPkg!!, currentSessionStart, eventTime)
                                    resumedActivities.remove(currentForegroundPkg)
                                }
                                currentForegroundPkg = pkg
                                currentSessionStart = eventTime
                            }
                        }

                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val classes = resumedActivities[pkg]
                            classes?.remove(className)

                            if (currentForegroundPkg == pkg && classes.isNullOrEmpty()) {
                                addSessionDuration(pkg, currentSessionStart, eventTime)
                                currentForegroundPkg = null
                                currentSessionStart = 0L
                                resumedActivities.remove(pkg)
                            }
                        }

                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val classes = resumedActivities[pkg]
                            classes?.remove(className)

                            if (currentForegroundPkg == pkg && classes.isNullOrEmpty()) {
                                addSessionDuration(pkg, currentSessionStart, eventTime)
                                currentForegroundPkg = null
                                currentSessionStart = 0L
                                resumedActivities.remove(pkg)
                            }
                        }

                        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                        UsageEvents.Event.KEYGUARD_SHOWN,
                        UsageEvents.Event.DEVICE_SHUTDOWN -> {
                            if (isScreenInteractive) {
                                if (currentForegroundPkg != null) {
                                    addSessionDuration(currentForegroundPkg!!, currentSessionStart, eventTime)
                                }
                                isScreenInteractive = false
                                currentSessionStart = 0L
                            }
                        }

                        UsageEvents.Event.SCREEN_INTERACTIVE,
                        UsageEvents.Event.KEYGUARD_HIDDEN -> {
                            isScreenInteractive = true
                            if (currentForegroundPkg != null && currentSessionStart == 0L) {
                                currentSessionStart = eventTime
                            }
                        }
                    }
                }

                // If user is currently in an app right now, credit session up to this exact moment
                if (isScreenInteractive && currentForegroundPkg != null && currentSessionStart > 0L) {
                    addSessionDuration(currentForegroundPkg!!, currentSessionStart, now)
                }
            }

            // Fallback to queryUsageStats if UsageEvents circular buffer returned no data
            if (perAppMillis.isEmpty()) {
                try {
                    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                    if (stats != null) {
                        for (stat in stats) {
                            val pkg = stat.packageName ?: continue
                            val time = stat.totalTimeInForeground
                            if (time > 0L) {
                                perAppMillis[pkg] = (perAppMillis[pkg] ?: 0L) + time
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Step 1: Filter to user-facing apps (keeps all games, downloaded apps, launchable system apps)
            val userApps = perAppMillis
                .filterKeys { pkg -> isUserFacingApp(pkg) }
                .toMutableMap()

            // Step 2: Global Safety Clamp (Proportionally scale down if sum exceeds max possible elapsed today)
            val maxPossibleElapsed = TimeWindowHelper.getMaxPossibleElapsedToday()
            val totalUserAppSum = userApps.values.sum()
            if (totalUserAppSum > maxPossibleElapsed && totalUserAppSum > 0L) {
                val scaleFactor = maxPossibleElapsed.toDouble() / totalUserAppSum.toDouble()
                for ((pkg, millis) in userApps.toList()) {
                    userApps[pkg] = (millis * scaleFactor).toLong()
                }
            }

            // Step 3: Per-app clamp & threshold (keep apps with >= 5 seconds of active foreground display)
            val filteredApps = userApps
                .mapValues { (_, millis) -> TimeWindowHelper.clampDuration(millis) }
                .filterValues { it >= 5_000L }

            val totalMillis = filteredApps.values.sum()
            val totalMinutes = Math.round(totalMillis.toDouble() / (1000.0 * 60.0)).toInt()

            if (totalMinutes <= 0 && filteredApps.isEmpty()) {
                return ScreenTimeBreakdown(0, emptyList())
            }

            // Build top apps + "Other Apps" bucket
            val sortedApps = filteredApps.entries.sortedByDescending { it.value }
            val topN = sortedApps.take(4)
            val others = sortedApps.drop(4)

            val resultList = mutableListOf<AppUsageInfo>()

            topN.forEachIndexed { index, entry ->
                val pkg = entry.key
                val mins = Math.round(entry.value.toDouble() / (1000.0 * 60.0)).toInt().coerceAtLeast(1)
                val pct = if (totalMillis > 0) (entry.value.toFloat() / totalMillis.toFloat()) * 100f else 0f
                val appLabel = if (pkg == context.packageName) {
                    "Productive Club"
                } else {
                    try {
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        }
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    }
                }

                resultList.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = appLabel,
                        usageMinutes = mins,
                        percentage = pct,
                        color = PALETTE[index % (PALETTE.size - 1)]
                    )
                )
            }

            if (others.isNotEmpty()) {
                val othersMillis = others.sumOf { it.value }
                val othersMins = Math.round(othersMillis.toDouble() / (1000.0 * 60.0)).toInt().coerceAtLeast(1)
                val pct = if (totalMillis > 0) (othersMillis.toFloat() / totalMillis.toFloat()) * 100f else 0f
                resultList.add(
                    AppUsageInfo(
                        packageName = "others",
                        appName = "Other Apps",
                        usageMinutes = othersMins,
                        percentage = pct,
                        color = PALETTE.last()
                    )
                )
            }

            ScreenTimeBreakdown(totalMinutes, resultList)
        } catch (e: Exception) {
            e.printStackTrace()
            ScreenTimeBreakdown(0, emptyList())
        }
    }

    fun formatMinutes(minutes: Int): String {
        return TimeWindowHelper.formatMinutes(minutes)
    }
}
