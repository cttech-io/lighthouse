package io.cttech.lighthouse.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Optional root extra: downloads a libretro core from libretro's own build server and installs it into RetroArch's
 * private `cores` folder, so a missing core is one tap instead of a trip through RetroArch's menus.
 * Only ever started by the user pressing "Download core"; never automatic. Without root RetroArch's folder can't be
 * written, so the dialog falls back to the manual instructions.
 */
object CoreInstaller {
    private const val TAG = "LighthouseCores"
    private const val BASE = "https://buildbot.libretro.com/nightly/android/latest"

    /** Biggest core zip we'll accept (the largest real ones are around 20 MB). */
    private const val MAX_ZIP_BYTES = 64 * 1024 * 1024

    private val safeName = Regex("""[a-z0-9_]+_libretro_android\.so""")
    private val safePackage = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")

    /** The package and file name inside a core path like `/data/user/0/<package>/cores/<file>.so`, or null if it isn't one. */
    fun parse(corePath: String): Pair<String, String>? {
        val rest = corePath.removePrefix("/data/user/0/").takeIf { it != corePath } ?: return null
        val pkg = rest.substringBefore('/')
        val file = rest.substringAfter("/cores/", "")
        return if (safePackage.matches(pkg) && safeName.matches(file)) pkg to file else null
    }

    /** libretro publishes 64-bit and 32-bit Android builds; RetroArch's `.aarch64` package is the 64-bit one. */
    fun downloadUrl(pkg: String, file: String): String =
        "$BASE/${if (pkg.endsWith(".aarch64")) "arm64-v8a" else "armeabi-v7a"}/$file.zip"

    /**
     * The one [file] inside a core zip, or null if it is missing or isn't a native library. Entry names other than
     * the expected one are ignored, so nothing else in the archive (or a path like `../x`) is ever written.
     */
    fun extract(zip: ByteArray, file: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val entry = z.nextEntry ?: return null
                if (entry.isDirectory || entry.name != file) continue
                val bytes = z.readBounded(MAX_ZIP_BYTES) ?: return null
                return bytes.takeIf { isElf(it) }
            }
        }
    }

    private fun isElf(b: ByteArray) =
        b.size > 4 && b[0] == 0x7F.toByte() && b[1] == 'E'.code.toByte() && b[2] == 'L'.code.toByte() && b[3] == 'F'.code.toByte()

    /**
     * Root shell that copies [staged] into RetroArch's cores folder (seen through init's root, as in
     * [io.cttech.lighthouse.launch.GameLauncher.coreCheckCommand]), owned by RetroArch and labelled for it, via a
     * temporary name so a half-written file is never mistaken for a core.
     */
    fun installCommand(pkg: String, file: String, staged: String): String {
        val dir = "/proc/1/root/data/user/0/$pkg/cores"
        val tmp = "$dir/.$file.part"
        return "[ -d ${Root.quote(dir)} ] && " +
            "cp ${Root.quote(staged)} ${Root.quote(tmp)} && " +
            "chown \$(stat -c %u:%g ${Root.quote(dir)}) ${Root.quote(tmp)} && " +
            "chmod 600 ${Root.quote(tmp)} && " +
            "{ restorecon -F ${Root.quote(tmp)} || true; } && " +
            "mv ${Root.quote(tmp)} ${Root.quote("$dir/$file")}"
    }

    /** Downloads and installs the core at [corePath]. True once the file is in place. */
    suspend fun install(context: Context, corePath: String): Boolean = withContext(Dispatchers.IO) {
        val (pkg, file) = parse(corePath) ?: return@withContext false
        val zip = download(downloadUrl(pkg, file)) ?: return@withContext false
        val core = extract(zip, file) ?: return@withContext false
        val staged = File(context.cacheDir, "cores").apply { mkdirs() }.resolve(file)
        try {
            staged.writeBytes(core)
            val ok = Root.run(installCommand(pkg, file, staged.absolutePath), timeoutSec = 30) != null
            Log.i(TAG, "install $file into $pkg: ${if (ok) "ok" else "failed"}")
            ok
        } finally {
            staged.delete()
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Lighthouse (+https://github.com/cttech-io/lighthouse)")
        try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readBounded(MAX_ZIP_BYTES) }
        } finally {
            c.disconnect()
        }
    }.onFailure { Log.w(TAG, "download failed: ${it.message}") }.getOrNull()
}
