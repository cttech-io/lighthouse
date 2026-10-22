package io.cttech.lighthouse.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroThumbnailsTest {
    private val base = "https://thumbnails.libretro.com"

    @Test fun exactNameFirstThenWithoutRevision() {
        val urls = LibretroThumbnails.candidateUrls("gba", "/sd/GBA/Golden Sun (USA, Europe) (Rev 1).gba")
        assertEquals("$base/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/Golden%20Sun%20%28USA%2C%20Europe%29%20%28Rev%201%29.png", urls.first())
        assertEquals("$base/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/Golden%20Sun%20%28USA%2C%20Europe%29.png", urls[1])
    }

    @Test fun unsafeCharactersBecomeUnderscores() {
        val urls = LibretroThumbnails.candidateUrls("snes", "/sd/SNES/Ys III: Wanderers (USA).sfc")
        assertTrue(urls.first(), urls.first().endsWith("/Ys%20III_%20Wanderers%20%28USA%29.png"))
    }

    @Test fun gbcAlsoTriesGameBoy() {
        val urls = LibretroThumbnails.candidateUrls("gbc", "/sd/GBC/Tetris (World).gb")
        assertTrue(urls.any { "/Nintendo%20-%20Game%20Boy/" in it })
    }

    @Test fun unknownPlatformHasNone() {
        assertEquals(emptyList<String>(), LibretroThumbnails.candidateUrls("switch", "/sd/Switch/Game.nsp"))
    }
}
