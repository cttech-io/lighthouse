package io.cttech.lighthouse.system

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Optional root extras (never required). Used where modern Android blocks ordinary apps, e.g.
 * silently toggling Wi-Fi/Bluetooth or reading the Wi-Fi name without location permission.
 * The first call makes Magisk (or similar) ask the user to allow Lighthouse.
 */
object Root {
    // LineageOS ships its own /product/bin/su that refuses apps, so prefer Magisk's binaries first.
    private val candidates = listOf("/debug_ramdisk/su", "/sbin/su", "/system/xbin/su", "/system/bin/su", "su")
    private val lock = Mutex()
    private var su: String? = null

    /** User switch (Settings): when false every root call fails as if the device weren't rooted. */
    @Volatile var enabled: Boolean = true

    /** True if a working su was found and granted. Cached after the first check. */
    suspend fun available(): Boolean = lock.withLock {
        if (!enabled) return@withLock false
        if (su == null) {
            // Generous timeout: the first call waits on the user answering Magisk's prompt.
            var refused = false
            su = withContext(Dispatchers.IO) {
                candidates.firstOrNull { path ->
                    val (ok, out) = probe(path, "id", timeoutSec = 30) ?: return@firstOrNull false
                    if (!ok && out.contains("Permission denied", ignoreCase = true)) refused = true
                    ok && out.contains("uid=0")
                }
            }
            // Only success is cached. Magisk remembers a denial (including a prompt left to time out) and won't
            // ask again, so the denial is surfaced instead: Settings points the user at Magisk's Superuser list.
            denied = su == null && refused
            Log.i("LighthouseRoot", "root ${if (su != null) "available via $su" else if (denied) "denied" else "unavailable"}")
        }
        su != null
    }

    /**
     * Forgets the cached answer and asks su again. A grant is cached for the life of the process, so without this a
     * grant revoked in Magisk would still read as granted and Magisk would never get the chance to prompt.
     */
    suspend fun recheck(): Boolean {
        lock.withLock { su = null; denied = false }
        return available()
    }

    /** True when su exists but refused Lighthouse at the last check; only the root manager can undo that. */
    @Volatile var denied: Boolean = false
        private set

    /** Single-quotes [s] for the shell, so file names with quotes or `$` can't break out of a root command. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Runs [command] as root; null if root is unavailable or the command failed. */
    suspend fun run(command: String, timeoutSec: Long = 8): String? {
        if (!available()) return null
        return withContext(Dispatchers.IO) { exec(su!!, command, timeoutSec) }
    }

    private fun exec(suPath: String, command: String, timeoutSec: Long = 8): String? =
        probe(suPath, command, timeoutSec)?.takeIf { it.first }?.second

    /** Runs su; (exited 0, output), or null if it couldn't start or timed out. */
    private fun probe(suPath: String, command: String, timeoutSec: Long = 8): Pair<Boolean, String>? = runCatching {
        val p = ProcessBuilder(suPath, "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroy(); return null }
        (p.exitValue() == 0) to out
    }.getOrNull()
}

/** Wi-Fi / Bluetooth switches and lookups that need root on Android 10+. */
object RootRadios {
    suspend fun setWifi(on: Boolean): Boolean = Root.run("svc wifi ${if (on) "enable" else "disable"}") != null

    suspend fun setBluetooth(on: Boolean): Boolean =
        Root.run("svc bluetooth ${if (on) "enable" else "disable"}") != null ||
            Root.run("cmd bluetooth_manager ${if (on) "enable" else "disable"}") != null

    /** Battery saver; `cmd power set-mode` on Android 10+, global setting as a fallback. */
    suspend fun setBatterySaver(on: Boolean): Boolean =
        Root.run("cmd power set-mode ${if (on) 1 else 0}") != null ||
            Root.run("settings put global low_power ${if (on) 1 else 0}") != null

    /** Connected network name from `cmd wifi status` ("Wifi is connected to \"Name\""). */
    suspend fun wifiName(): String? =
        Root.run("cmd wifi status")?.let { Regex("""connected to "([^"]+)"""").find(it)?.groupValues?.get(1) }
}
