package io.cttech.lighthouse.system

import androidx.annotation.RequiresApi
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.compose.runtime.Composable
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// ---------------------------------------------------------------- Wi-Fi

data class WifiState(val enabled: Boolean, val connected: Boolean, /** 0..4 */ val level: Int, val name: String? = null)

/** Android only reveals SSIDs when Location itself is switched on, not just permitted. */
fun Context.isLocationOn(): Boolean =
    getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled == true

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Live Wi-Fi state. The network name needs either location permission (+ Location on), which Android
 * requires for SSIDs, or root ([resolveNameWithRoot], used only inside the panel so Magisk isn't
 * prompted just by showing the top bar). [epoch] forces a re-read after a permission grant.
 */
@Composable
fun rememberWifiState(resolveNameWithRoot: Boolean = false, epoch: Int = 0): WifiState {
    val context = LocalContext.current
    val source = LocalAppSettings.current.wifiNameSource
    val useLocation = source == WifiNameSource.Auto || source == WifiNameSource.Location
    val useRoot = source == WifiNameSource.Auto || source == WifiNameSource.Root
    var state by remember { mutableStateOf(WifiState(false, false, 0)) }
    DisposableEffect(context, epoch, source) {
        val wifi = context.getSystemService(WifiManager::class.java)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        var connected = false
        var level = 0
        var name: String? = null
        fun publish() { state = WifiState(wifi.isWifiEnabled, connected, if (connected) level else 0, if (connected && source != WifiNameSource.Off) name else null) }

        // With location permission (Android 12+), ask for location info so WifiInfo carries the SSID.
        val callback = WifiCallback(
            includeLocation = Build.VERSION.SDK_INT >= 31 && useLocation && context.hasLocationPermission(),
            onCapabilities = { caps ->
                connected = true
                val info = caps.transportInfo as? WifiInfo
                level = info?.rssi?.let { wifi.calculateSignalLevel(it).coerceIn(0, 4) } ?: 3
                name = info?.ssid?.cleanSsid() ?: name
                publish()
            },
            onLost = { connected = false; name = null; publish() },
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = publish()
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        publish()
        onDispose {
            cm.unregisterNetworkCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }
    // Root fallback for the name (panel only). Location permission is preferred: give its callback a
    // moment to deliver the SSID first, and never touch root when it already has.
    LaunchedEffect(resolveNameWithRoot, state.connected, state.name == null, epoch, source) {
        if (resolveNameWithRoot && useRoot && state.connected && state.name == null) {
            if (useLocation && context.hasLocationPermission()) kotlinx.coroutines.delay(700)
            if (state.name == null) RootRadios.wifiName()?.let { state = state.copy(name = it) }
        }
    }
    return state
}

/** WifiInfo wraps SSIDs in quotes and reports "<unknown ssid>" without permission. */
private fun String.cleanSsid(): String? = removeSurrounding("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" }

// ---------------------------------------------------------------- Bluetooth

data class BluetoothState(
    val available: Boolean,
    val enabled: Boolean,
    /** Names of connected audio/input devices; null when we lack BLUETOOTH_CONNECT. */
    val connectedDevices: List<String>?,
)

/** Android 11 has no BLUETOOTH_CONNECT (it always reads as denied); the install-time BLUETOOTH permission applies. */
fun Context.hasBluetoothPermission(): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Live Bluetooth state. On/off needs no permission; device names need BLUETOOTH_CONNECT. [permissionEpoch] forces a re-read after granting. */
@Composable
fun rememberBluetoothState(permissionEpoch: Int = 0): BluetoothState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BluetoothState(false, false, null)) }
    DisposableEffect(context, permissionEpoch) {
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        val proxies = mutableMapOf<Int, BluetoothProfile>()

        @Suppress("MissingPermission") // guarded by hasBluetoothPermission()
        fun publish() {
            if (adapter == null) { state = BluetoothState(false, false, null); return }
            val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
            val devices = if (context.hasBluetoothPermission() && enabled) {
                proxies.values.flatMap { p -> runCatching { p.connectedDevices }.getOrDefault(emptyList()) }
                    .distinctBy(BluetoothDevice::getAddress)
                    .map { d -> runCatching { d.alias ?: d.name }.getOrNull() ?: "Unknown device" }
            } else if (enabled) null else emptyList()
            state = BluetoothState(true, enabled, devices)
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { proxies[profile] = proxy; publish() }
            override fun onServiceDisconnected(profile: Int) { proxies.remove(profile); publish() }
        }
        // Audio (A2DP / headset) and controllers/keyboards (HID host, constant 4 is hidden but stable).
        if (adapter != null && context.hasBluetoothPermission()) {
            listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4).forEach { runCatching { adapter.getProfileProxy(context, listener, it) } }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = publish()
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED) // system broadcasts
        publish()
        onDispose {
            context.unregisterReceiver(receiver)
            proxies.forEach { (profile, proxy) -> runCatching { adapter?.closeProfileProxy(profile, proxy) } }
        }
    }
    return state
}

// ---------------------------------------------------------------- Volume

data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

class VolumeControl(private val audio: AudioManager) {
    fun step(delta: Int) = audio.adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
        0, // no system UI: Lighthouse shows its own slider
    )
    fun toggleMute() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
    /** Absolute level (dragging the slider). Unmutes, like a hardware volume press. */
    fun set(level: Int) {
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
    }
}

/** Media volume, updated live (including hardware volume keys). No permission needed. */
@Composable
fun rememberVolume(): Pair<VolumeState, VolumeControl> {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    fun read() = VolumeState(
        audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )
    var state by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { state = read() }
        }
        // VOLUME_CHANGED_ACTION / STREAM_MUTE_CHANGED_ACTION are not public constants but are the standard signal.
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state to remember { VolumeControl(audio) }
}

// ---------------------------------------------------------------- Battery

data class BatteryState(
    val level: Int,
    val charging: Boolean,
    val full: Boolean,
    /** On a charger (Android reports "full", not "charging", at 100% while still plugged in). */
    val plugged: Boolean,
    /** Android's own estimate while charging, else null. */
    val chargeTimeRemainingMs: Long?,
    val temperatureC: Float?,
    val health: String,
    val powerSave: Boolean,
)

/** Live battery state from the sticky ACTION_BATTERY_CHANGED broadcast plus battery-saver mode. No permission. */
@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(-1, false, false, false, null, null, "Unknown", false)) }
    DisposableEffect(context) {
        val bm = context.getSystemService(android.os.BatteryManager::class.java)
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        var last: Intent? = null
        fun publish() {
            val i = last ?: return
            val raw = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            val full = status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val temp = i.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            state = BatteryState(
                level = if (raw >= 0) raw * 100 / scale else -1,
                charging = charging,
                full = full,
                plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0,
                chargeTimeRemainingMs = if (charging) bm.computeChargeTimeRemaining().takeIf { it > 0 } else null,
                temperatureC = if (temp != Int.MIN_VALUE) temp / 10f else null,
                health = when (i.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)) {
                    android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                    android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"
                    android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                    else -> "Unknown"
                },
                powerSave = pm.isPowerSaveMode,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_BATTERY_CHANGED) last = i
                publish()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        last = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        publish()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * NetworkCallback(flags) only exists on Android 12+; calling it on Android 11 crashes even with flags = 0
 * (NoSuchMethodError at startup), so older versions use the plain constructor.
 */
private class WifiCallback : ConnectivityManager.NetworkCallback {
    private val onCapabilities: (NetworkCapabilities) -> Unit
    private val onLostCb: () -> Unit

    @RequiresApi(31)
    private constructor(flags: Int, onCapabilities: (NetworkCapabilities) -> Unit, onLost: () -> Unit) : super(flags) {
        this.onCapabilities = onCapabilities; this.onLostCb = onLost
    }

    private constructor(onCapabilities: (NetworkCapabilities) -> Unit, onLost: () -> Unit) : super() {
        this.onCapabilities = onCapabilities; this.onLostCb = onLost
    }

    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCapabilities(caps)
    override fun onLost(network: Network) = onLostCb()

    companion object {
        operator fun invoke(includeLocation: Boolean, onCapabilities: (NetworkCapabilities) -> Unit, onLost: () -> Unit) =
            if (includeLocation && Build.VERSION.SDK_INT >= 31) WifiCallback(FLAG_INCLUDE_LOCATION_INFO, onCapabilities, onLost)
            else WifiCallback(onCapabilities, onLost)
    }
}
