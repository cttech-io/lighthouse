package io.cttech.lighthouse.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * Play time from Android's usage events: how long the game's app was actually in front between a launch and
 * Lighthouse coming back. Sleep, the screen turning off and switching to another app don't count. Needs the
 * special "Usage access" permission; without it nothing is measured.
 */
object PlayTime {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Android's Usage access screen, opened on Lighthouse where the system supports it. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Fallback when the system has no per-app page: the full list. */
    fun listIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Milliseconds [pkg] spent in front between [from] and [to]; null without Usage access. */
    fun measure(context: Context, pkg: String, from: Long, to: Long): Long? {
        if (!hasAccess(context)) return null
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { usage.queryEvents(from, to) }.getOrNull() ?: return null
        val list = ArrayList<Event>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == pkg || e.eventType in SYSTEM_EVENTS) list += Event(e.timeStamp, e.eventType, e.packageName, e.className)
        }
        return foregroundMs(list, pkg, from, to)
    }

    data class Event(val time: Long, val type: Int, val pkg: String, val activity: String?)

    /**
     * Adds up the stretches where any of [pkg]'s activities was resumed. An activity that pauses without having
     * resumed inside the window was already in front at [from]; one still resumed at the end runs to [to]. The
     * screen turning off or the device shutting down ends a stretch even if the pause event is missing.
     */
    internal fun foregroundMs(events: List<Event>, pkg: String, from: Long, to: Long): Long {
        val resumed = HashSet<String?>()
        var since = from
        var total = 0L
        var seenAny = false
        for (ev in events.sortedBy { it.time }) {
            val t = ev.time.coerceIn(from, to)
            when {
                ev.type in SYSTEM_EVENTS -> {
                    if (resumed.isNotEmpty()) total += t - since
                    resumed.clear()
                    seenAny = true
                }
                ev.pkg != pkg -> Unit
                ev.type == RESUMED -> {
                    if (resumed.isEmpty()) since = t
                    resumed += ev.activity
                    seenAny = true
                }
                ev.type == PAUSED || ev.type == STOPPED -> {
                    if (ev.activity in resumed) {
                        resumed -= ev.activity
                        if (resumed.isEmpty()) total += t - since
                    } else if (!seenAny && ev.type == PAUSED) {
                        total += t - from // in front since before the window
                    }
                    seenAny = true
                }
            }
        }
        if (resumed.isNotEmpty()) total += to - since
        return total
    }

    /** "under a minute", "45 min", "3 h 20 min", "12 h". */
    fun format(ms: Long): String {
        val minutes = ms / 60_000
        if (minutes < 1) return "under a minute"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    // UsageEvents.Event constants: ACTIVITY_RESUMED, ACTIVITY_PAUSED, ACTIVITY_STOPPED, SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN.
    internal const val RESUMED = UsageEvents.Event.ACTIVITY_RESUMED
    internal const val PAUSED = UsageEvents.Event.ACTIVITY_PAUSED
    internal const val STOPPED = UsageEvents.Event.ACTIVITY_STOPPED
    private val SYSTEM_EVENTS = setOf(UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN)
}
