package io.cttech.lighthouse.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.BuildConfig

/** Milestone 0 smoke test: proves build, install, controller focus and haptics on the device. */
@Composable
fun SmokeTestScreen() {
    var lastKey by remember { mutableStateOf("press any button") }
    val first = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0B1020), Color(0xFF05060A))))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) lastKey = event.key.toString()
                false
            }
            .padding(64.dp),
    ) {
        Column {
            Text("Lighthouse", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text(
                "v${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR} · milestone 0",
                color = Color(0xFF8FA3C8),
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                repeat(6) { i ->
                    Tile(index = i, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
            Spacer(Modifier.height(48.dp))
            Text("Last key: $lastKey", color = Color(0xFFCDD6E8), fontSize = 22.sp)
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
private fun Tile(index: Int, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tileScale")
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .scale(scale)
            .size(width = 160.dp, height = 240.dp)
            .background(Color(0xFF1A2236), shape)
            .border(if (focused) 4.dp else 0.dp, if (focused) Color(0xFF6FA8FF) else Color.Transparent, shape)
            .onFocusChanged {
                if (it.isFocused && !focused) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                focused = it.isFocused
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text("${index + 1}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    }
}
