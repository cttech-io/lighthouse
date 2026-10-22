package io.cttech.lighthouse.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class CrashReportsTest {
    private val info = CrashReports.DeviceInfo("0.1.9", 13, "sideload release", "15 (API 35)", "AYN Odin2 Portal")
    private val time = 1_761_170_400_000L // 2025-10-22 22:00:00 UTC

    private fun report(error: Throwable = IllegalStateException("boom")) =
        CrashReports.Report(time, CrashReports.format(info, "main", error, time))

    @Test fun formatHasVersionDeviceTimeAndTrace() {
        val text = report().text
        assertTrue(text, text.startsWith("Lighthouse 0.1.9 (13, sideload release)\nAndroid 15 (API 35) · AYN Odin2 Portal\n"))
        assertTrue(text, "Time: 2025-10-22 22:00:00 UTC · thread: main" in text)
        assertTrue(text, "java.lang.IllegalStateException: boom" in text)
        assertTrue(text, "at io.cttech.lighthouse.system.CrashReportsTest" in text)
    }

    @Test fun storagePathsLoseTheirFolderNames() {
        val text = report(RuntimeException("/storage/emulated/0/Sam's Games/Secret, Stuff/Zelda (USA).gba: open failed: ENOENT")).text
        assertFalse(text, "Secret" in text || "Sam" in text || "Zelda" in text)
        assertTrue(text, "RuntimeException: <storage>/…/file.gba: open failed: ENOENT" in text)
        assertEquals("open <storage>/…\nand \"<storage>/…\"", CrashReports.scrubPaths("open /sdcard/Games/PS2\nand \"/storage/1234-ABCD/My Roms\""))
    }

    @Test fun githubLinkIsPrefilledAndTitledByTheException() {
        val url = CrashReports.githubIssueUrl(report())
        assertTrue(url, url.startsWith("https://github.com/cttech-io/lighthouse/issues/new?labels=crash&title="))
        val title = URLDecoder.decode(url.substringAfter("title=").substringBefore("&"), "UTF-8")
        assertEquals("Crash: java.lang.IllegalStateException: boom", title)
        assertTrue("java.lang.IllegalStateException: boom" in URLDecoder.decode(url.substringAfter("body="), "UTF-8"))
    }

    @Test fun longTraceIsCutFromTheBottomToFitTheLink() {
        val deep = generateSequence(RuntimeException("inner") as Throwable) { RuntimeException("wrapped", it) }.take(60).last()
        val r = report(deep)
        val url = CrashReports.githubIssueUrl(r, maxLength = 4_000)
        assertTrue(url.length.toString(), url.length <= 4_000)
        val body = URLDecoder.decode(url.substringAfter("body="), "UTF-8")
        assertTrue(body, "Lighthouse 0.1.9 (13, sideload release)" in body) // the top stays
        assertTrue(body, "cut to fit" in body)
    }

    @Test fun mailtoGoesToTheProjectAddressWithSubjectAndReport() {
        val r = report()
        val uri = CrashReports.mailtoUri(r)
        assertTrue(uri, uri.startsWith("mailto:lighthouse@cttech.io?subject="))
        assertEquals("Lighthouse 0.1.9 crash", CrashReports.emailSubject(r))
        assertEquals(r.text, URLDecoder.decode(uri.substringAfter("body="), "UTF-8"))
    }
}
