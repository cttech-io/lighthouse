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
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
        content = {
            hints.forEach { (button, label) ->
                // Tappable for touch users (not focusable: the controller uses the real buttons).
                Row(
                    Modifier.clip(RoundedCornerShape(50)).pointerInput(button) { detectTapGestures { onTap(button) } }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ButtonGlyph(button, round = true)
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

/** Fades a horizontally scrolling row out at whichever edges have more content (tabs that don't fit). */
fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.LazyListState, width: Dp = 48.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx()
        if (state.canScrollForward) drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
            blendMode = BlendMode.DstIn,
        )
        if (state.canScrollBackward) drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
            blendMode = BlendMode.DstIn,
        )
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

