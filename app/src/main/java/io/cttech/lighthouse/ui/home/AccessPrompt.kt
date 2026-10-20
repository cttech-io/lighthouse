package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.ui.components.popupWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import io.cttech.lighthouse.ui.components.trapFocus
import io.cttech.lighthouse.theme.Lh
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run ask for "All files access" (sideload flavour), needed to find and watch ROM folders. */
@Composable
fun AccessPrompt(onGrant: () -> Unit, onDismiss: () -> Unit) {
    val grant = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().trapFocus().background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupWidth(560.dp)
                .background(Lh.colors.surface, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("Find your games", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Lighthouse needs \"All files access\" to find ROM folders (like Games/PS2 on your SD card) " +
                    "and to spot new games as soon as you copy them over. It only reads your game folders.",
                color = Lh.colors.text.copy(alpha = 0.85f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PromptButton("Grant access", Modifier.focusRequester(grant), onGrant)
                PromptButton("Not now", Modifier, onDismiss)
            }
        }
    }
    io.cttech.lighthouse.ui.components.FocusOnAppear(grant, Unit)
}

@Composable
private fun PromptButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(0.dp, Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onClick(); true } else false }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
