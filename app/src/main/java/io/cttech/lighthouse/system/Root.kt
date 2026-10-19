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

    /** True if a working su was found and granted. Cached after the first check. */
    suspend fun available(): Boolean = lock.withLock {
        if (su == null) {
            // Generous timeout: the first call waits on the user answering Magisk's prompt.
            su = withContext(Dispatchers.IO) { candidates.firstOrNull { exec(it, "id", timeoutSec = 30)?.contains("uid=0") == true } }
            // Only success is cached; Magisk remembers a denial itself, so retries are cheap.
            Log.i("LighthouseRoot", "root ${if (su != null) "available via $su" else "unavailable"}")
        }
        su != null
    }

    /** Runs [command] as root; null if root is unavailable or the command failed. */
    suspend fun run(command: String): String? {
        if (!available()) return null
        return withContext(Dispatchers.IO) { exec(su!!, command) }
    }

    private fun exec(suPath: String, command: String, timeoutSec: Long = 8): String? = runCatching {
        val p = ProcessBuilder(suPath, "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroy(); return null }
        if (p.exitValue() == 0) out else null
    }.getOrNull()
}

/** Wi-Fi / Bluetooth switches and lookups that need root on Android 10+. */
object RootRadios {
    suspend fun setWifi(on: Boolean): Boolean = Root.run("svc wifi ${if (on) "enable" else "disable"}") != null

    suspend fun setBluetooth(on: Boolean): Boolean =
        Root.run("svc bluetooth ${if (on) "enable" else "disable"}") != null ||
            Root.run("cmd bluetooth_manager ${if (on) "enable" else "disable"}") != null

    /** Connected network name from `cmd wifi status` ("Wifi is connected to \"Name\""). */
    suspend fun wifiName(): String? =
        Root.run("cmd wifi status")?.let { Regex("""connected to "([^"]+)"""").find(it)?.groupValues?.get(1) }
}
