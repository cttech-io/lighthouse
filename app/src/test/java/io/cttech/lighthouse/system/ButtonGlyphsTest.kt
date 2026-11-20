package io.cttech.lighthouse.system

import io.cttech.lighthouse.system.ButtonGlyphs.SELECT
import io.cttech.lighthouse.system.ButtonGlyphs.START
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonGlyphsTest {
    private fun glyphs(style: GlyphStyle, swapAB: Boolean = false) =
        listOf("A", "B", "X", "Y", "L1", "R1", START, SELECT).map { ButtonGlyphs.glyph(it, style, swapAB) }

    @Test fun odinPrintsOutlinedPlayAndSquareOnStartAndSelect() =
        assertEquals(listOf("A", "B", "X", "Y", "L1", "R1", "▷", "▢"), glyphs(GlyphStyle.Odin))

    @Test fun standardIsWhatLighthouseAlwaysShowed() =
        assertEquals(listOf("A", "B", "X", "Y", "L1", "R1", "☰", "⧉"), glyphs(GlyphStyle.Standard))

    @Test fun playStationShapes() =
        assertEquals(listOf("✕", "○", "□", "△", "L1", "R1", "Options", "Share"), glyphs(GlyphStyle.PlayStation))

    @Test fun nintendoLabelsAsPrintedNotAsAndroidNamesThem() =
        // Android's A is the bottom key, printed B (AOSP's Switch Pro key layout).
        assertEquals(listOf("B", "A", "Y", "X", "L", "R", "+", "−"), glyphs(GlyphStyle.Nintendo))

    @Test fun swappedABShowsTheKeyToPress() {
        assertEquals("B", ButtonGlyphs.glyph("A", GlyphStyle.Odin, swapAB = true))
        assertEquals("○", ButtonGlyphs.glyph("A", GlyphStyle.PlayStation, swapAB = true))
    }

    @Test fun detection() {
        assertEquals(GlyphStyle.Odin, ButtonGlyphs.forDevice("AYN", "Odin2 Portal"))
        assertEquals(GlyphStyle.Standard, ButtonGlyphs.forDevice("Retroid", "Retroid Pocket 5"))
        // The built-in pad follows the device; a plugged-in or Bluetooth pad follows its maker.
        assertEquals(GlyphStyle.Odin, ButtonGlyphs.forController(0x054C, external = false, "AYN", "Odin2 Portal"))
        assertEquals(GlyphStyle.PlayStation, ButtonGlyphs.forController(0x054C, external = true, "AYN", "Odin2 Portal"))
        assertEquals(GlyphStyle.Xbox, ButtonGlyphs.forController(0x045E, external = true, "AYN", "Odin2 Portal"))
        assertEquals(GlyphStyle.Nintendo, ButtonGlyphs.forController(0x057E, external = true, "AYN", "Odin2 Portal"))
        assertEquals(GlyphStyle.Standard, ButtonGlyphs.forController(0x2DC8, external = true, "AYN", "Odin2 Portal")) // 8BitDo
    }
}
