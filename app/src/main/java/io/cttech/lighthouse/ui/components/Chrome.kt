package io.cttech.lighthouse.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import io.cttech.lighthouse.theme.Lh
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.cttech.lighthouse.model.HomeTab
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Composable
fun TopBar(
    tabs: List<HomeTab>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Wide screens (the Portal is ~1067dp) get bigger tabs; small 4:3/square screens (RG405M ~430dp,
        // RG Cube ~480dp) get compact status icons and tighter spacing so the tabs keep room.
        val big = maxWidth >= 900.dp
        val compact = maxWidth < 640.dp
        val tabFont = if (big) 16.sp else 15.sp
        val tabPad = if (big) 14.dp else 12.dp
        Row(
            Modifier.fillMaxWidth().height(if (big) 58.dp else 52.dp).padding(horizontal = if (compact) 12.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // L1 · tabs · R1 take all the room the status icons leave (a weighted spacer used to claim half of it).
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                ButtonGlyph("L1")
                Spacer(Modifier.width(10.dp))
                val listState = rememberLazyListState()
                LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
                LazyRow(
                    state = listState,
                    userScrollEnabled = false,
                    // Tabs that don't fit fade out at the edge (instead of being cut mid-word), hinting at more.
                    modifier = Modifier.weight(1f, fill = false).fadingEdges(listState),
                ) {
                    itemsIndexed(tabs, key = { _, t -> t.key }) { i, tab ->
                        val active = i == selected
                        val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent, label = "tabBg")
                        val fg by animateColorAsState(if (active) Color.White else Lh.colors.textMuted, label = "tabFg")
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(bg)
                                // Tap to switch tabs. Not focusable: the controller uses L1/R1.
                                .pointerInput(i) { detectTapGestures { onSelect(i) } }
                                .padding(horizontal = tabPad, vertical = 6.dp),
                        ) {
                            Text(tab.label.uppercase(), color = fg, fontSize = tabFont, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.6.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                ButtonGlyph("R1")
            }
            // Always a clear gap between R1 and the status icons, however many tabs there are.
            Spacer(Modifier.width(if (big) 40.dp else if (compact) 12.dp else 24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalCompactChrome provides compact) {
                    status() // Wi-Fi · Bluetooth · Volume · Battery
                }
                Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                Clock(if (compact) 13.sp else 15.sp)
            }
        }
    }
}

@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier, onTap: (button: String) -> Unit = {}) {
    // Right-aligned, one line. On narrow screens hints are dropped from the left: the important ones
    // (Play, Favourite…) are listed last, so they're the ones that stay.
    Layout(
        modifier = modifier.fillMaxWidth().height(HintBarHeight).padding(horizontal = 16.dp),
        content = {
            hints.forEach { (button, label) ->
                // Tappable for touch users (not focusable: the controller uses the real buttons). A hint for a pair
                // ("L1 R1") shows both glyphs; each one taps as itself, the label as the last.
                val buttons = button.split(' ')
                Row(
                    Modifier.clip(RoundedCornerShape(50)).pointerInput(button) { detectTapGestures { onTap(buttons.last()) } }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    buttons.forEachIndexed { i, b ->
                        if (i > 0) Spacer(Modifier.width(4.dp))
                        Box(if (buttons.size > 1) Modifier.pointerInput(b) { detectTapGestures { onTap(b) } } else Modifier) { ButtonGlyph(b, round = true) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
            }
        },
    ) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        var used = 0
        val shown = placeables.indices.reversed().takeWhile { i ->
            val w = placeables[i].width + if (used > 0) gap else 0
            (used + w <= constraints.maxWidth).also { if (it) used += w }
        }.reversed()
        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = constraints.maxWidth - used
            shown.forEach { i ->
                val p = placeables[i]
                p.placeRelative(x, (constraints.maxHeight - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/** Height of [HintBar]; full-screen pop-ups leave this much room at the bottom so the bar never covers them. */
val HintBarHeight = 44.dp

/**
 * Which hints the bar shows. Pop-ups register theirs with [ProvideHints] while they're open; the newest wins,
 * so the bar always describes what the buttons do right now. With nothing open, the screen's own hints show.
 */
class HintHost {
    internal class Entry(hints: List<Pair<String, String>>, onTap: (String) -> Unit) {
        var hints by mutableStateOf(hints)
        var onTap by mutableStateOf(onTap)
    }
    internal val entries = androidx.compose.runtime.mutableStateListOf<Entry>()
}

val LocalHintHost = compositionLocalOf<HintHost?> { null }

/** While this is in composition, the hint bar shows [hints] (tapping one calls [onTap]). */
@Composable
fun ProvideHints(hints: List<Pair<String, String>>, onTap: (button: String) -> Unit = {}) {
    val host = LocalHintHost.current ?: return
    val entry = remember { HintHost.Entry(hints, onTap) }
    androidx.compose.runtime.SideEffect { entry.hints = hints; entry.onTap = onTap }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** The bar for [host]: the newest pop-up's hints, or [hints] when none is open. */
@Composable
fun HostedHintBar(host: HintHost, hints: List<Pair<String, String>>, modifier: Modifier = Modifier, onTap: (button: String) -> Unit = {}) {
    val top = host.entries.lastOrNull()
    HintBar(top?.hints ?: hints, modifier, top?.onTap ?: onTap)
}

/** The usual hints for a pop-up menu or dialog. */
fun popupHints(back: String = "Close") = listOf("B" to back, "A" to "Select")

/**
 * Fades a scrolling list out at whichever edges have more content: tabs that don't fit, or ([vertical])
 * grid rows below the screen, so a cut-off row reads as "more below" rather than clipped.
 */
fun Modifier.fadingEdges(
    state: androidx.compose.foundation.gestures.ScrollableState,
    width: Dp = 48.dp,
    vertical: Boolean = false,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx()
        val length = if (vertical) size.height else size.width
        fun edge(colors: List<Color>, from: Float, to: Float) = drawRect(
            if (vertical) Brush.verticalGradient(colors, startY = from, endY = to) else Brush.horizontalGradient(colors, startX = from, endX = to),
            blendMode = BlendMode.DstIn,
        )
        if (state.canScrollForward) edge(listOf(Color.Black, Color.Transparent), length - fade, length)
        if (state.canScrollBackward) edge(listOf(Color.Transparent, Color.Black), 0f, fade)
    }

/** True inside the top bar on small screens: status icons drop the battery percentage and shrink. */
val LocalCompactChrome = compositionLocalOf { false }

@Composable
fun ButtonGlyph(label: String, round: Boolean = false) {
    Box(
        Modifier
            .then(if (round) Modifier.size(22.dp) else Modifier.height(22.dp))
            .background(Color.White, if (round) CircleShape else RoundedCornerShape(6.dp))
            .padding(horizontal = if (round) 0.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Clock(size: androidx.compose.ui.unit.TextUnit) {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    Text(now.format(fmt), color = Color.White, fontSize = size, fontWeight = FontWeight.Medium)
}

