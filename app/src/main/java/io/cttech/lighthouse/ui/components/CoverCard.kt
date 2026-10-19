package io.cttech.lighthouse.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.model.Game

private val CoverShape = RoundedCornerShape(10.dp)

/**
 * A 2:3 cover tile. Until real artwork arrives (milestone 4) it draws a generated placeholder.
 * Handles its own focus visuals, haptic tick, and the A / X buttons.
 */
@Composable
fun CoverCard(
    game: Game,
    accent: Color,
    isFavourite: Boolean,
    modifier: Modifier = Modifier,
    focusedScale: Float = 1.06f,
    onFocused: () -> Unit,
    onLaunch: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "coverScale",
    )
    val ring = animateFloatAsState(if (focused) 1f else 0f, label = "coverRing") // read in draw phase
    val brush = remember(game.id, accent) { placeholderBrush(game, accent) }

    Box(
        modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.shadow(16.dp, CoverShape, ambientColor = accent, spotColor = accent) else Modifier)
            .background(brush, CoverShape)
            .drawWithContent {
                drawContent()
                val a = ring.value
                if (a > 0f) {
                    val w = 3.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = 0.92f * a),
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(w),
                    )
                }
            }
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onFocused()
                }
                focused = it.isFocused
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.ButtonA -> { onLaunch(); true }
                    Key.ButtonX -> { onToggleFavourite(); true }
                    else -> false
                }
            }
            // Handles touch, Enter and D-pad centre.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLaunch),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(
                monogram(game.title),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                game.title,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
        if (isFavourite) {
            Text("♥", color = Color(0xFFFF6A8A), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

private fun monogram(title: String): String =
    title.split(' ', ':', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2).joinToString("") { it.first().uppercase() }

/** Deterministic per-title gradient, nudged toward the platform accent. */
private fun placeholderBrush(game: Game, accent: Color): Brush {
    val h = (game.title.hashCode() and 0x7fffffff) % 360
    val base = Color.hsv(h.toFloat(), 0.55f, 0.55f)
    val top = lerp(base, accent, 0.35f)
    val bottom = lerp(base, Color.Black, 0.65f)
    return Brush.verticalGradient(listOf(top, bottom))
}
