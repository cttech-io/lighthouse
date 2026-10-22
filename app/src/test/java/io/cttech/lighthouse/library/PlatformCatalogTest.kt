package io.cttech.lighthouse.library

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformCatalogTest {
    @Test fun folderNamesBelongToOnePlatform() {
        val owners = PlatformCatalog.all.flatMap { p -> p.folderNames.map { it to p.id } }.groupBy({ it.first }, { it.second })
        owners.filterValues { it.size > 1 }.let { assertTrue("Folder names claimed twice: $it", it.isEmpty()) }
    }

    @Test fun matchesRetroFolderNames() {
        assertEquals("nes", PlatformCatalog.matchFolder("Famicom")?.id)
        assertEquals("snes", PlatformCatalog.matchFolder("Super Nintendo")?.id)
        assertEquals("megadrive", PlatformCatalog.matchFolder(" Genesis ")?.id)
        assertEquals("mastersystem", PlatformCatalog.matchFolder("Sega Master System")?.id)
        assertEquals("gamegear", PlatformCatalog.matchFolder("Game Gear")?.id)
        assertEquals("pcengine", PlatformCatalog.matchFolder("TurboGrafx-16")?.id)
        assertEquals("atari2600", PlatformCatalog.matchFolder("Atari 2600")?.id)
        assertEquals("3ds", PlatformCatalog.matchFolder("Nintendo 3DS")?.id)
        assertEquals("gamecube", PlatformCatalog.matchFolder("GameCube")?.id)
        assertEquals("wii", PlatformCatalog.matchFolder("Nintendo Wii")?.id)
        assertNull(PlatformCatalog.matchFolder("Dreamcast"))
    }

    @Test fun retroExtensions() {
        assertTrue("sfc" in PlatformCatalog.byId("snes")!!.extensions)
        assertTrue("gen" in PlatformCatalog.byId("megadrive")!!.extensions)
        assertTrue("pce" in PlatformCatalog.byId("pcengine")!!.extensions)
    }

    @Test fun everyProfilePlatformExists() {
        val profiles = JSONObject(File("src/main/assets/launch_profiles.json").readText()).getJSONArray("profiles")
        for (i in 0 until profiles.length()) {
            val p = profiles.getJSONObject(i)
            val platforms = p.getJSONArray("platforms")
            for (j in 0 until platforms.length()) {
                assertTrue("${p.getString("id")} -> ${platforms.getString(j)}", PlatformCatalog.byId(platforms.getString(j)) != null)
            }
        }
    }
}
