package io.cttech.lighthouse.system

import android.content.Context
import android.os.Build
import io.cttech.lighthouse.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Opt-in crash reports. A crash is written to a small file in app storage and nothing else: the next launch
 * asks what to do with it (see CrashReportDialog), and it only leaves the device if the person sends it
 * themselves, from their browser, email app or the share sheet.
 */
object CrashReports {
    const val EMAIL = "lighthouse@cttech.io"
    private const val KEEP = 3
    private const val PREFS = "crash_reports"
    private const val KEY_SEEN = "seen_until"
    private const val MAX_BYTES = 64 * 1024

    /** Records uncaught exceptions, then hands over to Android's own handler (so the crash still happens). */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, format(DeviceInfo.current(), thread.name, error, System.currentTimeMillis())) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The newest report, if there is one. */
    fun latest(context: Context): Report? = files(context).firstOrNull()?.let(::read)

    /** The newest report if the person hasn't been asked about it yet; asking marks every report as seen. */
    fun takeUnseen(context: Context): Report? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val newest = latest(context) ?: return null
        if (newest.time <= prefs.getLong(KEY_SEEN, 0)) return null
        prefs.edit().putLong(KEY_SEEN, newest.time).apply()
        return newest
    }

    fun deleteAll(context: Context) { files(context).forEach { it.delete() } }

    data class Report(val time: Long, val text: String)

    data class DeviceInfo(val version: String, val versionCode: Int, val flavour: String, val android: String, val device: String) {
        companion object {
            fun current() = DeviceInfo(
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE}",
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }

    // --- Pure parts (unit-tested) ------------------------------------------------------------------------

    /** The report text: what crashed and where, with storage paths (which can hold personal folder names) removed. */
    fun format(info: DeviceInfo, thread: String, error: Throwable, time: Long): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        return buildString {
            appendLine("Lighthouse ${info.version} (${info.versionCode}, ${info.flavour})")
            appendLine("Android ${info.android} · ${info.device}")
            appendLine("Time: ${utc(time)} · thread: $thread")
            appendLine()
            append(scrubPaths(trace))
        }
    }

    /**
     * Paths under shared storage become "<storage>/…" (plus "/file.ext" when the path ends in one): folder and
     * file names can be personal and contain spaces, so everything up to the next ": ", quote or line end goes,
     * which is where Android's own messages end a path ("…/x.gba: open failed: ENOENT").
     */
    fun scrubPaths(text: String): String =
        Regex("""/(?:storage|sdcard|mnt/media_rw)/(?:(?!: )[^"\n\t])*""").replace(text) { m ->
            val ext = Regex("""\.([A-Za-z0-9]{1,5})\s*$""").find(m.value)?.groupValues?.get(1)
            "<storage>/…" + if (ext != null) "/file.$ext" else ""
        }

    /** A pre-filled "new issue" link; the trace is cut from the bottom so the whole link stays under [maxLength]. */
    fun githubIssueUrl(report: Report, maxLength: Int = 7_500): String {
        val title = "Crash: " + (firstLine(report.text) ?: "Lighthouse closed unexpectedly")
        fun url(body: String) = "https://github.com/cttech-io/lighthouse/issues/new?labels=crash&title=${enc(title)}&body=${enc(body)}"
        fun body(text: String, cut: Boolean) =
            "What were you doing when it closed?\n\n\n```\n$text${if (cut) "\n… (cut to fit; Share log in Lighthouse has the full report)" else ""}\n```\n"
        val lines = report.text.lines()
        var keep = lines.size
        var link = url(body(report.text, cut = false))
        while (link.length > maxLength && keep > 1) {
            keep = maxOf(1, keep - maxOf(1, (keep / 8)))
            link = url(body(lines.take(keep).joinToString("\n"), cut = true))
        }
        return link
    }

    /** mailto: link with the subject and the report as the body (RFC 6068); the email app shows it before sending. */
    fun mailtoUri(report: Report): String =
        "mailto:$EMAIL?subject=${enc(emailSubject(report))}&body=${enc(report.text)}"

    fun emailSubject(report: Report): String = "Lighthouse " + (report.text.lineSequence().firstOrNull()?.removePrefix("Lighthouse ")?.substringBefore(" ") ?: "") + " crash"

    /** The exception line ("java.lang.IllegalStateException: …") for the issue title. */
    private fun firstLine(text: String): String? =
        text.lineSequence().dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }?.take(100)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun utc(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))

    // --- Files ------------------------------------------------------------------------------------------------

    private fun dir(context: Context) = File(context.filesDir, "crashes")

    /** Newest first. */
    private fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }.orEmpty()
            .sortedByDescending { it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0 }

    private fun save(context: Context, text: String) {
        val dir = dir(context).apply { mkdirs() }
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(text.take(MAX_BYTES / 4)) // chars: at most 3 bytes each in UTF-8, so it always reads back
        files(context).drop(KEEP).forEach { it.delete() }
    }

    private fun read(file: File): Report? = runCatching {
        val time = file.name.removePrefix("crash-").removeSuffix(".txt").toLong()
        file.readBounded(MAX_BYTES)?.decodeToString()?.let { Report(time, it) }
    }.getOrNull()
}
