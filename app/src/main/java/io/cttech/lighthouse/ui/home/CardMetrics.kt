package io.cttech.lighthouse.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.cttech.lighthouse.system.CardSize

/**
 * Cover card sizes for the shelves, worked out from the space the shelf actually has, so the same
 * Small/Medium/Large setting looks right on a 7" 16:9 screen and a small 4:3 or square one.
 * Calibrated so Medium on the Odin2 Portal (body 1067×504dp) matches the original fixed sizes.
 */
internal data class CardMetrics(
    /** Carousel: width of an unfocused card's slot, and how much wider the focused card is. */
    val carouselSlot: Dp,
    val carouselExtra: Dp,
    /** Grid: minimum card width; the grid fits as many columns as that allows. */
    val gridMin: Dp,
    /** HOME dashboard: card widths for the hero row (Continue playing) and the other rows. */
    val heroWidth: Dp,
    val rowWidth: Dp,
) {
    val carouselGrowth: Float get() = carouselExtra / carouselSlot
    /** Height the carousel row needs: the focused card (2:3) plus padding. */
    val carouselRowHeight: Dp get() = (carouselSlot + carouselExtra) * 1.5f + 32.dp

    companion object {
        private const val CAROUSEL_CHROME = 8 + 64 + 32 // top padding + title block + row padding (dp)

        fun of(size: CardSize, width: Dp, height: Dp): CardMetrics {
            // Carousel: the focused card takes a share of the tallest card that fits.
            val maxFocused = ((height.value - CAROUSEL_CHROME) / 1.5f).coerceAtLeast(90f)
            val (share, ratio) = when (size) {
                CardSize.Small -> 0.68f to 1.5f
                CardSize.Medium -> 0.83f to 220f / 150f
                CardSize.Large -> 0.95f to 1.35f
            }
            var focused = maxFocused * share
            // Narrow screens: keep at least ~4 cards across.
            focused = focused.coerceAtMost((width.value - 80f) / 4.5f * ratio)
            val slot = focused / ratio

            // Grid and dashboard: scale with the shorter of width/height relative to the Portal.
            val scale = minOf(height.value / 504f, width.value / 1067f).coerceIn(0.6f, 1.4f)
            val factor = when (size) { CardSize.Small -> 0.8f; CardSize.Medium -> 1f; CardSize.Large -> 1.25f }
            val gridMin = (150f * factor * scale.coerceAtMost(1f)).coerceAtMost((height.value - 60f) / 1.5f * 0.95f)
            return CardMetrics(
                carouselSlot = slot.dp,
                carouselExtra = (focused - slot).dp,
                gridMin = gridMin.coerceAtLeast(72f).dp,
                heroWidth = (150f * factor * scale).dp,
                rowWidth = (112f * factor * scale).dp,
            )
        }
    }
}

internal val LocalCardMetrics = staticCompositionLocalOf { CardMetrics(150.dp, 70.dp, 150.dp, 150.dp, 112.dp) }
