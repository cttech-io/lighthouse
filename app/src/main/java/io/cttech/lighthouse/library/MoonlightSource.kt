package io.cttech.lighthouse.library

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.util.Log
import io.cttech.lighthouse.system.Root

/** One app (game, Desktop, Steam Big Picture…) a paired PC offers through Moonlight. */
data class MoonlightApp(val hostUuid: String, val hostName: String, val appId: Int, val title: String, val hdr: Boolean) {
    /** Stable locator: renaming the PC or the app never loses favourites or covers. */
    val locator: String get() = "$hostUuid/$appId"
}

/**
 * Moonlight's PCs and their app lists. Moonlight keeps each PC's app list only in its private cache, so reading
 * it needs root; launching and box art don't (Moonlight exports its shortcut activity and a poster provider).
 */
object MoonlightSource {
    const val PACKAGE = "com.limelight"
    // Through init's mount namespace: a root shell started by an app inherits Android's app data isolation,
    // which hides other apps' /data/data folders.
    private const val DATA = "/proc/1/root/data/data/$PACKAGE"
    private const val TAG = "LighthouseMoonlight"

    /** Last read, by locator: the launch needs the PC name and HDR flag, which the locator doesn't carry. */
    @Volatile private var known: Map<String, MoonlightApp> = emptyMap()

    fun installed(context: Context): Boolean = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun app(locator: String): MoonlightApp? = known[locator]

    /** Every PC's cached app list; null when it can't be read (no root), so callers keep what they had. */
    suspend fun read(context: Context): List<MoonlightApp>? {
        val raw = Root.run("for f in $DATA/cache/applist/*; do [ -f \"\$f\" ] && echo \"$FILE_MARK\$(basename \"\$f\")\" && cat \"\$f\" && echo; done; echo done")
            ?: return null
        val names = hostNames(context)
        val apps = raw.split(FILE_MARK).drop(1).flatMap { chunk ->
            val uuid = chunk.substringBefore('\n').trim()
            parseAppList(chunk.substringAfter('\n')).map { (id, title, hdr) ->
                MoonlightApp(uuid, names[uuid.uppercase()] ?: "PC", id, title, hdr)
            }
        }
        known = apps.associateBy { it.locator }
        Log.i(TAG, "read ${apps.size} apps from ${apps.map { it.hostUuid }.distinct().size} PCs")
        return apps
    }

    /** UUID -> PC name: Moonlight's database (root), else its per-PC launcher shortcuts (Lighthouse is home). */
    private suspend fun hostNames(context: Context): Map<String, String> {
        val fromDb = Root.run("sqlite3 $DATA/databases/computers4.db 'select UUID, ComputerName from Computers' 2>/dev/null")
            ?.lines()?.mapNotNull { l -> l.split('|', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }
            ?.toMap().orEmpty()
        if (fromDb.isNotEmpty()) return fromDb
        return runCatching {
            val la = context.getSystemService(LauncherApps::class.java)
            if (!la.hasShortcutHostPermission()) return emptyMap()
            val q = LauncherApps.ShortcutQuery().setPackage(PACKAGE)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            la.getShortcuts(q, Process.myUserHandle()).orEmpty().associate { it.id.uppercase() to it.shortLabel.toString() }
        }.getOrDefault(emptyMap())
    }

    /** Moonlight's launch shortcut: wakes the PC if needed (Wake-on-LAN), then starts the app. */
    fun launchIntent(app: MoonlightApp): Intent = Intent(Intent.ACTION_VIEW)
        .setClassName(PACKAGE, "com.limelight.ShortcutTrampoline")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra("UUID", app.hostUuid)
        .putExtra("Name", app.hostName)
        .putExtra("AppId", app.appId.toString())
        .putExtra("AppName", app.title)
        .putExtra("HDR", app.hdr)

    /** The box art Moonlight downloaded from the PC (PNG), via its exported poster provider. */
    fun boxArt(context: Context, locator: String): ByteArray? = runCatching {
        val uri = Uri.parse("content://poster.$PACKAGE/boxart/$locator")
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private const val FILE_MARK = "@@LIGHTHOUSE_FILE "

    /** Sunshine/GFE `applist` XML -> (id, title, hdr). Plain parsing so it's unit-testable off-device. */
    fun parseAppList(xml: String): List<Triple<Int, String, Boolean>> =
        Regex("<App>(.*?)</App>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            fun tag(name: String) = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(m.groupValues[1])?.groupValues?.get(1)?.trim()
            val id = tag("ID")?.toIntOrNull() ?: return@mapNotNull null
            val title = tag("AppTitle")?.let(::unescape)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Triple(id, title, tag("IsHdrSupported") == "1")
        }.toList()

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
}
