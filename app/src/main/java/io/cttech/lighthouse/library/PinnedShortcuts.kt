package io.cttech.lighthouse.library

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Shortcuts apps pin to the home screen ("Create shortcut", "Add to home screen"). As the default home app
 * Lighthouse receives those requests (PinShortcutActivity); Android keeps the pinned list, so this only reads it.
 */
object PinnedShortcuts {
    private const val TAG = "LighthouseShortcuts"

    data class Pinned(val pkg: String, val id: String, val label: String) {
        val locator: String get() = "$pkg/$id"
    }

    private fun launcherApps(context: Context) = context.getSystemService(LauncherApps::class.java)

    /** False when Lighthouse isn't the default home app: only that app may read and launch pinned shortcuts. */
    fun available(context: Context): Boolean = runCatching { launcherApps(context).hasShortcutHostPermission() }.getOrDefault(false)

    /** Every enabled shortcut pinned to Lighthouse; null when they can't be read (not the home app). */
    fun all(context: Context): List<Pinned>? {
        if (!available(context)) return null
        return runCatching {
            query(context, null, null).filter { it.isEnabled }.map { Pinned(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString()) }
        }.onFailure { Log.w(TAG, "can't read pinned shortcuts", it) }.getOrNull()
    }

    fun launch(context: Context, pkg: String, id: String): Boolean = runCatching {
        launcherApps(context).startShortcut(pkg, id, null, null, Process.myUserHandle()); true
    }.onFailure { Log.w(TAG, "launch $pkg/$id failed", it) }.getOrDefault(false)

    /** Unpins one shortcut (the others from that app stay pinned). */
    fun remove(context: Context, pkg: String, id: String): Boolean = runCatching {
        val keep = query(context, pkg, null).map { it.id }.filter { it != id }
        launcherApps(context).pinShortcuts(pkg, keep, Process.myUserHandle()); true
    }.onFailure { Log.w(TAG, "unpin $pkg/$id failed", it) }.getOrDefault(false)

    /** The shortcut's icon as a PNG, for use as its cover. */
    fun iconPng(context: Context, pkg: String, id: String): ByteArray? = runCatching {
        val info = query(context, pkg, listOf(id)).firstOrNull() ?: return null
        val d = launcherApps(context).getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) ?: return null
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size); d.draw(Canvas(bmp))
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); bmp.recycle() }.toByteArray()
    }.getOrNull()

    private fun query(context: Context, pkg: String?, ids: List<String>?): List<ShortcutInfo> {
        val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        if (pkg != null) q.setPackage(pkg)
        if (ids != null) q.setShortcutIds(ids)
        return launcherApps(context).getShortcuts(q, Process.myUserHandle()).orEmpty()
    }

    /**
     * Moonlight names a pinned game "<PC uuid><app id>" and labels it "<game> (<PC>)". Those become the same
     * entries as Moonlight's root-read games, so a game is never listed twice.
     */
    fun asMoonlightApp(p: Pinned): MoonlightApp? {
        if (p.pkg != MoonlightSource.PACKAGE || p.id.length <= 36) return null
        val appId = p.id.substring(36).toIntOrNull() ?: return null
        val m = Regex("""^(.*) \((.+)\)$""").find(p.label)
        return MoonlightApp(p.id.substring(0, 36), m?.groupValues?.get(2) ?: "", appId, m?.groupValues?.get(1) ?: p.label, hdr = false)
    }
}
