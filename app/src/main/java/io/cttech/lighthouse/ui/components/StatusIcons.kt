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
import androidx.compose.ui.graphics.luminance
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

/** Magnifying glass: Search. */
@Composable
fun SearchGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(tint, radius = u(6.5f), center = Offset(u(10.5f), u(10.5f)), style = stroke(2.2f))
        drawLine(tint, Offset(u(15.5f), u(15.5f)), Offset(u(20.5f), u(20.5f)), strokeWidth = u(2.4f), cap = StrokeCap.Round)
    }
}

/** Wired network: a box on top joined to two below, like a network diagram. */
@Composable
fun EthernetGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val box = Size(u(6f), u(5f))
        drawRect(tint, Offset(u(9f), u(3f)), box, style = stroke(2f))
        drawRect(tint, Offset(u(3f), u(16f)), box, style = stroke(2f))
        drawRect(tint, Offset(u(15f), u(16f)), box, style = stroke(2f))
        drawLine(tint, Offset(u(12f), u(8f)), Offset(u(12f), u(12f)), strokeWidth = u(2f), cap = StrokeCap.Round)
        drawLine(tint, Offset(u(6f), u(12f)), Offset(u(18f), u(12f)), strokeWidth = u(2f), cap = StrokeCap.Round)
        drawLine(tint, Offset(u(6f), u(12f)), Offset(u(6f), u(16f)), strokeWidth = u(2f), cap = StrokeCap.Round)
        drawLine(tint, Offset(u(18f), u(12f)), Offset(u(18f), u(16f)), strokeWidth = u(2f), cap = StrokeCap.Round)
    }
}

/** Small filled dot in the top-right corner of a status glyph: Tailscale is on. */
@Composable
fun VpnBadge(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(tint, radius = u(3.2f), center = Offset(u(20.5f), u(3.5f)))
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

/** Battery level (percent) below which the icon turns red. */
const val LOW_BATTERY_PERCENT = 15

/**
 * Fill colour for the battery icon: green while plugged in, else red when low, else amber in battery saver, else
 * [normal]. Low beats saver because Android switches saver on at about this level, so the warning would never show.
 * [onLightBackground] picks deeper shades for when the icon sits on the white focus pill.
 */
fun batteryFillColor(level: Int, plugged: Boolean, powerSave: Boolean, normal: Color, onLightBackground: Boolean): Color = when {
    plugged -> if (onLightBackground) Color(0xFF1E8E3E) else Color(0xFF34C759)
    level in 0 until LOW_BATTERY_PERCENT -> if (onLightBackground) Color(0xFFC62828) else Color(0xFFFF5252)
    powerSave -> if (onLightBackground) Color(0xFFB26A00) else Color(0xFFFFB300)
    else -> normal
}

/**
 * Battery: outline with a clear gap around the fill (so it reads cleanly at 100%), terminal nub, and a fill coloured
 * by state ([batteryFillColor]). Colour is never the only cue: a bolt marks charging and a plus marks battery saver.
 */
@Composable
fun BatteryGlyph(level: Int, plugged: Boolean, powerSave: Boolean, tint: Color) {
    Canvas(Modifier.size(width = 30.dp, height = 22.dp)) {
        val stroke = 1.6.dp.toPx()
        val gap = 1.4.dp.toPx()
        val nubW = 2.2.dp.toPx()
        val left = stroke / 2
        val right = size.width - nubW - 1.dp.toPx()
        val top = size.height * 0.22f
        val bottom = size.height * 0.78f
        val radius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx())
        drawRoundRect(tint, Offset(left, top), Size(right - left, bottom - top), radius, style = Stroke(stroke))
        val nubH = (bottom - top) * 0.42f
        drawRoundRect(tint, Offset(right + 1.dp.toPx(), (size.height - nubH) / 2), Size(nubW, nubH), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))

        val inset = stroke / 2 + gap
        val innerLeft = left + inset
        val innerTop = top + inset
        val innerW = (right - left) - inset * 2
        val innerH = (bottom - top) - inset * 2
        // The focus pill is white and turns the icon black, so a dark tint means a light background.
        val fill = batteryFillColor(level, plugged, powerSave, tint, onLightBackground = tint.luminance() < 0.5f)
        val frac = level.coerceIn(0, 100) / 100f
        if (frac > 0f) {
            drawRoundRect(fill, Offset(innerLeft, innerTop), Size(innerW * frac, innerH), androidx.compose.ui.geometry.CornerRadius(1.6.dp.toPx()))
        }
        if (plugged) {
            // Bolt centred in the body, slightly taller than the fill so it reads at a glance.
            val cx = left + (right - left) / 2
            val cy = size.height / 2
            val bh = (bottom - top) * 0.92f
            val bw = bh * 0.55f
            val bolt = Path().apply {
                moveTo(cx + bw * 0.15f, cy - bh / 2)
                lineTo(cx - bw / 2, cy + bh * 0.06f)
                lineTo(cx - bw * 0.02f, cy + bh * 0.06f)
                lineTo(cx - bw * 0.15f, cy + bh / 2)
                lineTo(cx + bw / 2, cy - bh * 0.06f)
                lineTo(cx + bw * 0.02f, cy - bh * 0.06f)
                close()
            }
            drawPath(bolt, Color(0xFF0B0E14))
        } else if (powerSave) {
            // Battery saver's plus sign, so the state doesn't rely on the amber alone.
            val cx = left + (right - left) / 2
            val cy = size.height / 2
            val arm = (bottom - top) * 0.28f
            val w = 1.8.dp.toPx()
            drawLine(Color(0xFF0B0E14), Offset(cx - arm, cy), Offset(cx + arm, cy), w, StrokeCap.Round)
            drawLine(Color(0xFF0B0E14), Offset(cx, cy - arm), Offset(cx, cy + arm), w, StrokeCap.Round)
        }
    }
}

// What TalkBack says for each status icon: the same facts the glyph draws, in words.

fun wifiDescription(enabled: Boolean, connected: Boolean, level: Int): String = when {
    !enabled -> "Wi-Fi off"
    !connected -> "Wi-Fi on, not connected"
    else -> "Wi-Fi connected, signal ${level.coerceIn(1, 4)} of 4"
}

/** The Network button: wired wins over Wi-Fi (Android prefers it too), then Tailscale if it's on. */
fun networkDescription(wifiEnabled: Boolean, wifiConnected: Boolean, wifiLevel: Int, ethernet: Boolean, tailscale: Boolean): String =
    (if (ethernet) "Ethernet connected" else wifiDescription(wifiEnabled, wifiConnected, wifiLevel)) + if (tailscale) ", Tailscale on" else ""

fun bluetoothDescription(enabled: Boolean, connected: Boolean): String = when {
    !enabled -> "Bluetooth off"
    connected -> "Bluetooth on, device connected"
    else -> "Bluetooth on"
}

fun volumeDescription(level: Int, max: Int, muted: Boolean): String =
    if (muted || level == 0) "Volume muted" else "Volume ${if (max > 0) level * 100 / max else level} percent"

fun batteryDescription(level: Int, plugged: Boolean, powerSave: Boolean): String =
    "Battery $level percent" + when {
        plugged -> ", charging"
        powerSave -> ", battery saver on"
        else -> ""
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
