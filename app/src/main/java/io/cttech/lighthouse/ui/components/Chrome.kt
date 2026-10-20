package io.cttech.lighthouse.ui.components

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
        // Wide screens (the Portal is ~1067dp) get bigger tabs; small 4:3/square screens keep the compact size.
        val big = maxWidth >= 900.dp
        val tabFont = if (big) 17.sp else 15.sp
        val tabPad = if (big) 16.dp else 14.dp
        Row(
            Modifier.fillMaxWidth().height(if (big) 58.dp else 52.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // L1 · tabs · R1 take all the room the status icons leave (a weighted spacer used to claim half of it).
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                ButtonGlyph("L1")
                Spacer(Modifier.width(10.dp))
                val listState = rememberLazyListState()
                LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
                LazyRow(state = listState, modifier = Modifier.weight(1f, fill = false), userScrollEnabled = false) {
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
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                status() // Wi-Fi · Bluetooth · Volume · Battery
                Spacer(Modifier.width(14.dp))
                Clock()
            }
        }
    }
}

@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier, onTap: (button: String) -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(44.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        hints.forEach { (button, label) ->
            // Tappable for touch users (not focusable: the controller uses the real buttons).
            Row(
                Modifier.clip(RoundedCornerShape(50)).pointerInput(button) { detectTapGestures { onTap(button) } }.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ButtonGlyph(button, round = true)
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}

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
private fun Clock() {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    Text(now.format(fmt), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

