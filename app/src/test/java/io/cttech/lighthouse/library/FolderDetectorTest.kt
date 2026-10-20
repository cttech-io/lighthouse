package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderDetectorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(path: String): File = File(tmp.root, path).apply { parentFile!!.mkdirs(); writeText("x") }
    private fun dir(path: String): File = File(tmp.root, path).apply { mkdirs() }

    @Test fun skipsGameNativeInstallFolderButKeepsShortcutFolders() {
        dir("GameNative/Steam/steamapps/common/Lossless Scaling") // GameNative's own game installs
        dir("GameNative/Epic/games")
        touch("Games/GameNative Shortcuts/Red Dead Redemption.steam")
        touch("Games/DroidDeck/Red Dead Redemption.steam")
        val found = FolderDetector.detect(listOf(tmp.root)).map { it.platformId to it.dir.relativeTo(tmp.root).path }
        assertEquals(
            listOf("droiddeck" to "Games/DroidDeck", "gamenative" to "Games/GameNative Shortcuts"),
            found.sortedBy { it.second },
        )
    }

    @Test fun gameNativeOtherStoresAreScanned() {
        touch("GN/Red Dead Redemption.steam")
        touch("GN/Fortnite.epic")
        touch("GN/The Witcher.gog")
        touch("GN/Fallout 3.amazon")
        touch("GN/My Game.pcgame")
        touch("GN/.droiddeck-sync.json")
        val titles = RomScanner.scan(PlatformCatalog.byId("gamenative")!!, listOf(File(tmp.root, "GN"))).map { it.title }.sorted()
        assertEquals(listOf("Fallout 3", "Fortnite", "My Game", "Red Dead Redemption", "The Witcher"), titles)
    }
}
