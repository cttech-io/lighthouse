package io.cttech.lighthouse.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which labels the on-screen button hints use, so they match what's printed on the controller in your hands.
 * [Auto] follows the controller last used: the built-in one by device model, a plugged-in or Bluetooth one by maker.
 */
enum class GlyphStyle(val label: String) {
    Auto("Automatic"),
    Standard("Standard (A B X Y, ☰ ⧉)"),
    Odin("AYN Odin (A B X Y, ▷ ▢)"),
    Xbox("Xbox (A B X Y, LB RB, ☰ ⧉)"),
    PlayStation("PlayStation (✕ ○ □ △)"),
    Nintendo("Nintendo (B A Y X, L R, + −)"),
}

/**
 * Hints name buttons as Android does ("A", "B", "X", "Y", "L1", "R1", "Start", "Select"; "◀▶" for left/right) and
 * [glyph] turns that into what the controller shows.
 */
object ButtonGlyphs {
    const val START = "Start"
    const val SELECT = "Select"
    const val LEFT_RIGHT = "◀▶"

    private const val SONY = 0x054C
    private const val MICROSOFT = 0x045E
    private const val NINTENDO = 0x057E

    /** The style of the controller last used, for [GlyphStyle.Auto]; set by the activity as keys arrive. */
    var detected by mutableStateOf(GlyphStyle.Standard)

    /** Built-in controls: AYN's Odin handhelds print an outlined ▷ on Start and ▢ on Select. */
    fun forDevice(manufacturer: String, model: String): GlyphStyle =
        if (manufacturer.contains("AYN", ignoreCase = true) && model.contains("odin", ignoreCase = true)) GlyphStyle.Odin else GlyphStyle.Standard

    /** The built-in controls get the device's style; a plugged-in or Bluetooth pad its maker's, else Standard. */
    fun forController(vendorId: Int, external: Boolean, manufacturer: String, model: String): GlyphStyle = when {
        !external -> forDevice(manufacturer, model)
        vendorId == SONY -> GlyphStyle.PlayStation
        vendorId == MICROSOFT -> GlyphStyle.Xbox
        vendorId == NINTENDO -> GlyphStyle.Nintendo
        else -> GlyphStyle.Standard
    }

    /**
     * What to draw for [button]. With [swapAB] (Settings → Controls → Button layout) Lighthouse's A is the controller's
     * B key and the other way round, so the hint shows the key to press.
     */
    fun glyph(button: String, style: GlyphStyle, swapAB: Boolean): String {
        val key = when (button) {
            "A" -> if (swapAB) "B" else "A"
            "B" -> if (swapAB) "A" else "B"
            else -> button
        }
        if (key == LEFT_RIGHT) return "◀ ▶"
        return when (style) {
            GlyphStyle.Auto, GlyphStyle.Standard -> when (key) { START -> "☰"; SELECT -> "⧉"; else -> key }
            GlyphStyle.Odin -> when (key) { START -> "▷"; SELECT -> "▢"; else -> key }
            GlyphStyle.Xbox -> when (key) { START -> "☰"; SELECT -> "⧉"; "L1" -> "LB"; "R1" -> "RB"; else -> key }
            GlyphStyle.PlayStation -> when (key) {
                "A" -> "✕"; "B" -> "○"; "X" -> "□"; "Y" -> "△"; START -> "Options"; SELECT -> "Share"; else -> key
            }
            // Android gives Nintendo pads' keys position names: the bottom key, printed B, is its A, and the top one,
            // printed X, its Y (AOSP's Vendor_057e_Product_2009.kl). Show what's printed.
            GlyphStyle.Nintendo -> when (key) {
                "A" -> "B"; "B" -> "A"; "X" -> "Y"; "Y" -> "X"; "L1" -> "L"; "R1" -> "R"; START -> "+"; SELECT -> "−"; else -> key
            }
        }
    }

    /** What TalkBack calls the key, by the controller's own name for it. */
    fun spoken(button: String, style: GlyphStyle, swapAB: Boolean): String = when (val g = glyph(button, style, swapAB)) {
        "☰", "▷", "+" -> if (style == GlyphStyle.Nintendo) "Plus" else "Start"
        "⧉", "▢", "−" -> if (style == GlyphStyle.Nintendo) "Minus" else "Select"
        "✕" -> "Cross"
        "○" -> "Circle"
        "□" -> "Square"
        "△" -> "Triangle"
        "◀ ▶" -> "Left or right"
        else -> g
    }
}
