package io.cttech.lighthouse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Hand-drawn status glyphs on a 24-unit grid; no icon-font dependency.

private val Off = Color.White.copy(alpha = 0.32f)

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.stroke(w: Float = 2f) = Stroke(width = u(w), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Wi-Fi fan: [level] 0..4 lights that many parts (dot + 3 arcs); off draws everything dim with a slash. */
@Composable
fun WifiGlyph(enabled: Boolean, connected: Boolean, level: Int, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val c = Offset(u(12f), u(19f))
        val lit = if (enabled && connected) level.coerceIn(1, 4) else 0
        drawCircle(if (lit >= 1) tint else Off, radius = u(1.8f), center = c)
        listOf(5f, 9.5f, 14f).forEachIndexed { i, r ->
            drawArc(
                color = if (lit >= i + 2) tint else Off,
                startAngle = -135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(c.x - u(r), c.y - u(r)), size = Size(u(r) * 2, u(r) * 2),
                style = stroke(2.2f),
            )
        }
        if (!enabled) drawLine(tint, Offset(u(4f), u(4f)), Offset(u(20f), u(20f)), strokeWidth = u(2.2f), cap = StrokeCap.Round)
    }
}

/** Bluetooth rune; dots either side when a device is connected. */
@Composable
fun BluetoothGlyph(enabled: Boolean, connected: Boolean, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val col = if (enabled) tint else Off
        val p = Path().apply {
            moveTo(u(7f), u(7f)); lineTo(u(17f), u(16.5f)); lineTo(u(12f), u(21.5f)); lineTo(u(12f), u(2.5f))
            lineTo(u(17f), u(7.5f)); lineTo(u(7f), u(17f))
        }
        drawPath(p, col, style = stroke(2f))
        if (enabled && connected) {
            drawCircle(tint, u(1.5f), Offset(u(3.5f), u(12f)))
            drawCircle(tint, u(1.5f), Offset(u(20.5f), u(12f)))
        }
    }
}

/** Battery outline filled to [level]; bolt while charging; amber when saver is on, red when low. */
@Composable
fun BatteryGlyph(level: Int, charging: Boolean, powerSave: Boolean, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val body = androidx.compose.ui.geometry.Rect(u(2f), u(7f), u(20f), u(17f))
        drawRoundRect(tint, topLeft = body.topLeft, size = body.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(2.5f)), style = stroke(1.8f))
        drawRoundRect(tint, topLeft = Offset(u(20.8f), u(10f)), size = Size(u(1.8f), u(4f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(0.8f)))
        val frac = (level.coerceIn(0, 100)) / 100f
        val fill = when {
            powerSave -> Color(0xFFFFB300)
            level in 0..15 && !charging -> Color(0xFFFF5252)
            else -> tint
        }
        if (frac > 0f) {
            drawRoundRect(fill, topLeft = Offset(u(3.8f), u(8.8f)), size = Size((u(16.4f)) * frac, u(6.4f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(1.2f)))
        }
        if (charging) {
            val bolt = Path().apply {
                moveTo(u(12.5f), u(5f)); lineTo(u(8f), u(12.8f)); lineTo(u(11.2f), u(12.8f))
                lineTo(u(10f), u(19f)); lineTo(u(14.5f), u(11.2f)); lineTo(u(11.3f), u(11.2f)); close()
            }
            drawPath(bolt, Color.Black.copy(alpha = 0.55f), style = stroke(2.2f))
            drawPath(bolt, Color(0xFFFFD166))
        }
    }
}

/** Speaker with 0–2 waves by level; a cross when muted or at zero. */
@Composable
fun VolumeGlyph(level: Int, max: Int, muted: Boolean, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val body = Path().apply {
            moveTo(u(3f), u(9f)); lineTo(u(7f), u(9f)); lineTo(u(12f), u(4.5f)); lineTo(u(12f), u(19.5f))
            lineTo(u(7f), u(15f)); lineTo(u(3f), u(15f)); close()
        }
        drawPath(body, tint)
        val silent = muted || level == 0
        if (silent) {
            drawLine(tint, Offset(u(15.5f), u(9f)), Offset(u(21.5f), u(15f)), u(2f), StrokeCap.Round)
            drawLine(tint, Offset(u(21.5f), u(9f)), Offset(u(15.5f), u(15f)), u(2f), StrokeCap.Round)
        } else {
            val waves = if (level * 2 > max) 2 else 1
            listOf(4.5f, 8.5f).take(waves).forEach { r ->
                drawArc(tint, -50f, 100f, false, Offset(u(12f) - u(r), u(12f) - u(r)), Size(u(r) * 2, u(r) * 2), style = stroke(2f))
            }
        }
    }
}
