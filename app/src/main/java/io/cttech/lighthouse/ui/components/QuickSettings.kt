package io.cttech.lighthouse.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.system.RootRadios
import io.cttech.lighthouse.system.hasLocationPermission
import io.cttech.lighthouse.system.isLocationOn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import io.cttech.lighthouse.system.hasBluetoothPermission
import io.cttech.lighthouse.system.rememberBatteryState
import io.cttech.lighthouse.system.rememberBluetoothState
import io.cttech.lighthouse.system.rememberVolume
import io.cttech.lighthouse.system.rememberWifiState

enum class QuickPanel { Wifi, Bluetooth, Volume, Battery }

private val PanelBg = Color(0xFF151A26)
private val Muted = Color(0xFFB7C1D6)

/**
 * The three status icons for the top bar. Each is focusable (D-pad Up from the shelf) and tappable;
 * selecting one calls [onOpen]. [requesters] lets the caller return focus to the icon when its panel closes.
 */
@Composable
fun QuickSettingsIcons(requesters: Map<QuickPanel, FocusRequester>, btEpoch: Int, onOpen: (QuickPanel) -> Unit) {
    val wifi = rememberWifiState()
    val bt = rememberBluetoothState(btEpoch)
    val (vol, _) = rememberVolume()
    val battery = rememberBatteryState()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusButton(requesters.getValue(QuickPanel.Wifi), { onOpen(QuickPanel.Wifi) }) { tint ->
            WifiGlyph(wifi.enabled, wifi.connected, wifi.level, tint)
        }
        if (bt.available) {
            StatusButton(requesters.getValue(QuickPanel.Bluetooth), { onOpen(QuickPanel.Bluetooth) }) { tint ->
                BluetoothGlyph(bt.enabled, !bt.connectedDevices.isNullOrEmpty(), tint)
            }
        }
        StatusButton(requesters.getValue(QuickPanel.Volume), { onOpen(QuickPanel.Volume) }) { tint ->
            VolumeGlyph(vol.level, vol.max, vol.muted, tint)
        }
        if (battery.level >= 0) {
            StatusButton(requesters.getValue(QuickPanel.Battery), { onOpen(QuickPanel.Battery) }, wide = true) { tint ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BatteryGlyph(battery.level, battery.plugged, battery.powerSave, tint)
                    Text("${battery.level}%", color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StatusButton(requester: FocusRequester, onClick: () -> Unit, wide: Boolean = false, glyph: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(if (wide) Modifier.height(36.dp).padding(horizontal = 0.dp) else Modifier.size(36.dp))
            .clip(if (wide) RoundedCornerShape(50) else CircleShape)
            .then(if (wide) Modifier.background(Color.Transparent).padding(horizontal = 0.dp) else Modifier)
            .background(if (focused) Color.White else Color.Transparent)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onClick(); true } else false }
            .clickable(onClick = onClick)
            .then(if (wide) Modifier.padding(horizontal = 10.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) { glyph(if (focused) Color.Black else Color.White) }
}

/**
 * Drop-down panel under the top bar. B / Back closes it ([onClose]); focus is trapped inside while open.
 * Rule: in-app control where Android allows it, else the system's floating panel, else full settings.
 */
@Composable
fun QuickSettingsPanel(panel: QuickPanel, btEpoch: Int, onBtPermission: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape)) { onClose(); true } else false
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                .padding(top = 56.dp, end = 20.dp)
                .width(380.dp)
                .background(PanelBg, RoundedCornerShape(18.dp))
                .clickable(indication = null, interactionSource = null) {} // swallow taps inside
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (panel) {
                QuickPanel.Wifi -> WifiPanel(first)
                QuickPanel.Bluetooth -> BluetoothPanel(first, btEpoch, onBtPermission)
                QuickPanel.Volume -> VolumePanel(first)
                QuickPanel.Battery -> BatteryPanel(first)
            }
        }
    }
    FocusOnAppear(first, panel)
}

@Composable
private fun WifiPanel(first: FocusRequester) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var epoch by remember { mutableIntStateOf(0) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { epoch++ }
    val wifi = rememberWifiState(resolveNameWithRoot = true, epoch = epoch)
    PanelTitle("Wi-Fi")
    StatusLine(
        when {
            !wifi.enabled -> "Off"
            !wifi.connected -> "Not connected"
            wifi.name != null -> "Connected to ${wifi.name}"
            else -> "Connected"
        },
        if (wifi.connected) Color.White else Muted,
    )
    // Silent with root; otherwise Android 10+ only allows its own floating Wi-Fi panel to switch it.
    PillButton(if (wifi.enabled) "Turn Wi-Fi off" else "Turn Wi-Fi on", Modifier.focusRequester(first)) {
        scope.launch { if (!RootRadios.setWifi(!wifi.enabled)) context.openFirst(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS) }
    }
    if (wifi.enabled) {
        PillButton("Choose network") { context.openFirst(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS) }
    }
    if (wifi.connected && wifi.name == null) {
        // Without root, Android only reveals network names with location permission AND Location switched on.
        when {
            !context.hasLocationPermission() -> {
                StatusLine("Android shares the network name only with apps allowed to use location. Lighthouse uses it just for this.")
                PillButton("Show network name") { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            }
            !context.isLocationOn() -> {
                StatusLine("Location is off, so Android hides the network name.")
                PillButton("Turn on Location") { context.openFirst(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
            }
        }
    }
    PillButton("Wi-Fi settings") { context.openFirst(Settings.ACTION_WIFI_SETTINGS) }
}

@Composable
private fun BluetoothPanel(first: FocusRequester, btEpoch: Int, onPermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bt = rememberBluetoothState(btEpoch)
    // Without root, "turn on" is Android's small confirmation dialog over Lighthouse (not the settings app).
    val requestEnable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    PanelTitle("Bluetooth")
    StatusLine(if (bt.enabled) "On" else "Off", if (bt.enabled) Color.White else Muted)
    PillButton(if (bt.enabled) "Turn Bluetooth off" else "Turn Bluetooth on", Modifier.focusRequester(first)) {
        scope.launch {
            val turningOn = !bt.enabled
            if (RootRadios.setBluetooth(turningOn)) return@launch
            when {
                !turningOn -> context.openFirst(Settings.ACTION_BLUETOOTH_SETTINGS) // apps can't switch it off
                !context.hasBluetoothPermission() -> onPermission()
                else -> runCatching { requestEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    .onFailure { context.openFirst(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
        }
    }
    if (bt.enabled) {
        when {
            bt.connectedDevices == null -> PillButton("Show connected devices", onClick = onPermission)
            bt.connectedDevices.isEmpty() -> StatusLine("No devices connected")
            else -> bt.connectedDevices.forEach { StatusLine("• $it", Color.White) }
        }
    }
    // Pairing new devices stays a system screen.
    PillButton("Pair a device") { context.openFirst(Settings.ACTION_BLUETOOTH_SETTINGS) }
}

@Composable
private fun VolumePanel(first: FocusRequester) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val (vol, control) = rememberVolume()
    PanelTitle("Sound")
    VolumeSlider(vol.level, vol.max, vol.muted, Modifier.focusRequester(first), onStep = control::step, onSet = control::set)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(if (vol.muted) "Unmute" else "Mute") { control.toggleMute() }
        PillButton("Haptics: ${if (settings.hapticsEnabled) "on" else "off"}") { settings.setHaptics(!settings.hapticsEnabled) }
    }
    PillButton("All volume controls") { context.openFirst(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS) }
}

@Composable
private fun BatteryPanel(first: FocusRequester) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = rememberBatteryState()
    PanelTitle("Battery")
    StatusLine(
        "${b.level}% · " + when {
            b.full && b.plugged -> "Fully charged · plugged in"
            b.full -> "Fully charged"
            b.charging -> b.chargeTimeRemainingMs?.let { "Charging · ${formatDuration(it)} to full" } ?: "Charging"
            else -> "On battery"
        },
        Color.White,
    )
    StatusLine(listOfNotNull(b.temperatureC?.let { "Temperature %.0f °C".format(it) }, "Health ${b.health.lowercase()}").joinToString(" · "))
    // Silent with root; otherwise the system battery-saver screen (apps can't switch it).
    PillButton("Battery saver: ${if (b.powerSave) "on" else "off"}", Modifier.focusRequester(first)) {
        scope.launch {
            if (!RootRadios.setBatterySaver(!b.powerSave)) context.openFirst(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
    }
    PillButton("Battery usage") { context.openFirst(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS) }
}

private fun formatDuration(ms: Long): String {
    val mins = (ms / 60_000).coerceAtLeast(1)
    return if (mins >= 60) "${mins / 60} h ${mins % 60} m" else "$mins min"
}

/** Focusable slider: D-pad left/right steps media volume; touch drags or taps to any level. */
@Composable
private fun VolumeSlider(level: Int, max: Int, muted: Boolean, modifier: Modifier, onStep: (Int) -> Unit, onSet: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onStep(-1); true }
                Key.DirectionRight -> { onStep(+1); true }
                else -> false
            }
        }
        .focusable(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Media volume", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (muted) "Muted" else "$level / $max", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        val frac = if (muted || max == 0) 0f else level / max.toFloat()
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp) // generous touch target around a thin track
                .pointerInput(max) {
                    fun toLevel(x: Float) = ((x / size.width) * max).roundToInt().coerceIn(0, max)
                    detectTapGestures { onSet(toLevel(it.x)) }
                }
                .pointerInput(max) {
                    fun toLevel(x: Float) = ((x / size.width) * max).roundToInt().coerceIn(0, max)
                    detectHorizontalDragGestures(onDragStart = { onSet(toLevel(it.x)) }) { change, _ -> onSet(toLevel(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = if (focused) 10.dp else 8.dp
            Box(Modifier.fillMaxWidth().height(track).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)))
            Box(Modifier.fillMaxWidth(frac).height(track).clip(RoundedCornerShape(50)).background(if (focused) Color.White else Color(0xFF6FA8FF)))
            val thumb = 22.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * frac)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        if (focused) {
            Spacer(Modifier.height(4.dp))
            Text("◀ ▶ to adjust", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PanelTitle(text: String) = Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
private fun StatusLine(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 15.sp, lineHeight = 20.sp)

@Composable
fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onClick(); true } else false }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Starts the first intent action that resolves (system panel, then full settings page). */
private fun Context.openFirst(vararg actions: String) {
    for (action in actions) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

/** Runtime BLUETOOTH_CONNECT request; [onResult] bumps the epoch so Bluetooth state re-reads. */
@Composable
fun rememberBluetoothPermissionRequest(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
}

@Composable
fun rememberBtEpoch(): Pair<Int, () -> Unit> {
    var epoch by remember { mutableIntStateOf(0) }
    return epoch to { epoch++ }
}
