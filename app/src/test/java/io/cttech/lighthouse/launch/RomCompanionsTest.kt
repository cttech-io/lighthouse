package io.cttech.lighthouse.launch

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomCompanionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(path: String, text: String = "x") = File(tmp.root, path).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun names(rom: File) = RomCompanions.of(rom).map { it.relativeTo(tmp.root.canonicalFile).path }

    @Test fun cueSheetTracks() {
        file("PS1/Crash Bandicoot (USA).bin")
        file("PS1/track2.bin")
        val cue = file("PS1/Crash Bandicoot (USA).cue",
            "FILE \"Crash Bandicoot (USA).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\nfile track2.bin binary\r\nFILE \"missing.bin\" BINARY\n")
        assertEquals(listOf("PS1/Crash Bandicoot (USA).bin", "PS1/track2.bin"), names(cue))
    }

    @Test fun playlistDiscsAndTheirTracks() {
        file("PS1/FF7/Disc 1.bin"); file("PS1/FF7/Disc 2.chd")
        file("PS1/FF7/Disc 1.cue", "FILE \"Disc 1.bin\" BINARY")
        val m3u = file("PS1/FF7/FF7.m3u", "# Final Fantasy VII\nDisc 1.cue\n\nDisc 2.chd\n")
        assertEquals(listOf("PS1/FF7/Disc 1.cue", "PS1/FF7/Disc 1.bin", "PS1/FF7/Disc 2.chd"), names(m3u))
    }

    @Test fun nothingOutsideTheGamesFolder() {
        file("secret.db")
        file("PS1/other/x.bin")
        val cue = file("PS1/Game/Game.cue", "FILE \"../../secret.db\" BINARY\nFILE \"/etc/hosts\" BINARY\nFILE \"..\\other\\x.bin\" BINARY")
        assertEquals(emptyList<String>(), names(cue))
    }

    @Test fun otherFormatsHaveNone() {
        assertEquals(emptyList<String>(), names(file("PS1/Game.chd")))
    }
}
