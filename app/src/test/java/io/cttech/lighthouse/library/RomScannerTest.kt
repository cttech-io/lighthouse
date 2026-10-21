package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(path: String): File = File(tmp.root, path).apply { parentFile!!.mkdirs(); writeText("x") }
    private fun platform(id: String) = PlatformCatalog.byId(id)!!

    @Test fun switchSkipsUpdatesDlcKeysAndSaves() {
        touch("Switch/ROMs/Breath of the Wild/The Legend of Zelda Breath of the Wild.xci")
        touch("Switch/ROMs/Breath of the Wild/The Legend of Zelda Breath of the Wild 1.6.0 Update/BotW [01007EF00011E800][v786432].nsp")
        touch("Switch/ROMs/Lets Go Pikachu/Pokemon Lets Go Pikachu/Pokemon Lets Go Pikachu.xci")
        touch("Switch/ROMs/Lets Go Pikachu/Pokemon Lets Go Pikachu v131072/Pokemon Lets Go Pikachu v131072.nsp")
        touch("Switch/Keys/prod.keys")
        touch("Switch/Save data 2026-09-09/01007EF00011E000/save.nsp")
        touch("Switch/Firmware/Firmware 17.0.0.zip")
        val titles = RomScanner.scan(platform("switch"), listOf(File(tmp.root, "Switch"))).map { it.title }
        assertEquals(listOf("The Legend of Zelda Breath of the Wild", "Pokemon Lets Go Pikachu"), titles)
    }

    @Test fun ps2SkipsBiosAndReadmes() {
        touch("PS2/ps2_bios/scph39001.bin")
        touch("PS2/ROMs/Grand Theft Auto - San Andreas (USA) (v3.00)/Grand Theft Auto - San Andreas (USA) (v3.00).iso")
        touch("PS2/ROMs/Grand Theft Auto - San Andreas (USA) (v3.00)/Vimm's Lair.txt")
        val titles = RomScanner.scan(platform("ps2"), listOf(File(tmp.root, "PS2"))).map { it.title }
        assertEquals(listOf("Grand Theft Auto: San Andreas"), titles)
    }

    @Test fun binWithCueIsOneGame() {
        touch("PS2/Game (USA).cue")
        touch("PS2/Game (USA).bin")
        assertEquals(1, RomScanner.scan(platform("ps2"), listOf(File(tmp.root, "PS2"))).size)
    }

    @Test fun duplicateCopiesAcrossFoldersCollapse() {
        touch("sd/Switch/BotW/The Legend of Zelda Breath of the Wild.xci")
        touch("internal/Switch/Breath of the Wild/The Legend of Zelda Breath of the Wild.xci")
        val roms = RomScanner.scan(platform("switch"), listOf(File(tmp.root, "sd/Switch"), File(tmp.root, "internal/Switch")))
        assertEquals(1, roms.size)
        assert(roms.single().path.contains("/sd/"))
    }

    @Test fun detectorFindsPlatformFoldersUnderTrimmedContainers() {
        File(tmp.root, "sd/Games/PS2").mkdirs()
        File(tmp.root, "sd/Games/GameNative Shortcuts").mkdirs()
        File(tmp.root, "sd/Games/Wii U").mkdirs()          // no platform yet: ignored
        File(tmp.root, "internal/Games /Switch").mkdirs()  // real folder name has a trailing space
        val found = FolderDetector.detect(listOf(File(tmp.root, "sd"), File(tmp.root, "internal")))
            .map { it.platformId }.toSet()
        assertEquals(setOf("ps2", "gamenative", "switch"), found)
    }

    @Test fun pspFolderAndFormats() {
        File(tmp.root, "sd/ROMs/Sony - PlayStation Portable").mkdirs()
        assertEquals(listOf("psp"), FolderDetector.detect(listOf(File(tmp.root, "sd"))).map { it.platformId })
        listOf("Patapon (Europe).iso", "Lumines.cso", "Daxter.chd", "EBOOT.PBP", "notes.txt").forEach { touch("PSP/$it") }
        assertEquals(4, RomScanner.scan(platform("psp"), listOf(File(tmp.root, "PSP"))).size)
    }

    @Test fun ps1ListsSheetsNotTracks() {
        File(tmp.root, "sd/Games/PSX").mkdirs()
        assertEquals(listOf("ps1"), FolderDetector.detect(listOf(File(tmp.root, "sd"))).map { it.platformId })
        listOf("Crash Bandicoot (USA).cue", "Crash Bandicoot (USA).bin", "Spyro.chd", "FF7.m3u").forEach { touch("PS1/$it") }
        assertEquals(3, RomScanner.scan(platform("ps1"), listOf(File(tmp.root, "PS1"))).size)
    }
}
