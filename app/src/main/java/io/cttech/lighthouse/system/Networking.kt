package io.cttech.lighthouse.system

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.net.Inet4Address
import java.net.InetAddress

// ---------------------------------------------------------------- Ethernet and VPN state

/** A wired connection (USB-C dock or adapter). [address]: its IPv4 address, if it has one. */
data class EthernetState(val connected: Boolean, val address: String? = null)

/** Tailscale's VPN as Android sees it. [address]: the device's 100.x address on the tailnet. */
data class TailscaleState(val installed: Boolean, val connected: Boolean, val address: String? = null)

/** Live Ethernet state; needs no permission beyond ACCESS_NETWORK_STATE. */
@Composable
fun rememberEthernetState(): EthernetState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(EthernetState(false)) }
    DisposableEffect(context) {
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
        val callback = LinkCallback { links -> state = EthernetState(links.isNotEmpty(), links.values.firstNotNullOfOrNull { it.ipv4() }) }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.registerNetworkCallback(request, callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return state
}

/**
 * Whether Tailscale's VPN is up. Android shows every app the VPN its traffic goes through, but not which app runs it,
 * so a VPN counts as Tailscale when it carries a tailnet address.
 */
@Composable
fun rememberTailscaleState(): TailscaleState {
    val context = LocalContext.current
    val installed = remember(context) { Tailscale.installed(context) }
    var state by remember { mutableStateOf(TailscaleState(installed, false)) }
    DisposableEffect(context, installed) {
        if (!installed) return@DisposableEffect onDispose { }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = LinkCallback { links ->
            val address = links.values.flatMap { lp -> lp.linkAddresses.map { it.address } }.firstOrNull(Tailscale::isTailnetAddress)
            state = TailscaleState(installed = true, connected = address != null, address = address?.hostAddress)
        }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.registerNetworkCallback(request, callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return state
}

/** Keeps the link properties of every matching network; [onChange] gets them all after each change. */
private class LinkCallback(private val onChange: (Map<Network, LinkProperties>) -> Unit) : ConnectivityManager.NetworkCallback() {
    private val links = HashMap<Network, LinkProperties>()

    override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
        synchronized(links) { links[network] = lp; onChange(HashMap(links)) }
    }

    override fun onLost(network: Network) {
        synchronized(links) { links.remove(network); onChange(HashMap(links)) }
    }
}

private fun LinkProperties.ipv4(): String? = linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }?.hostAddress

// ---------------------------------------------------------------- Tailscale control

/**
 * Tailscale's Android app accepts these broadcasts from any app (its exported IPNReceiver). Connecting only works once
 * Tailscale is signed in and allowed as a VPN; otherwise Tailscale posts a notification asking the user to open it.
 */
object Tailscale {
    const val PACKAGE = "com.tailscale.ipn"
    private const val RECEIVER = "com.tailscale.ipn.IPNReceiver"

    fun installed(context: Context): Boolean = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun connect(context: Context) = send(context, "com.tailscale.ipn.CONNECT_VPN")

    fun disconnect(context: Context) = send(context, "com.tailscale.ipn.DISCONNECT_VPN")

    /** [name] is the exit node's name as Tailscale shows it; null or empty stops using one. */
    fun useExitNode(context: Context, name: String?, allowLanAccess: Boolean) = send(context, "com.tailscale.ipn.USE_EXIT_NODE") {
        putExtra("exitNode", name.orEmpty())
        putExtra("allowLanAccess", allowLanAccess)
    }

    fun launchIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}): Boolean = runCatching {
        context.sendBroadcast(Intent(action).setClassName(PACKAGE, RECEIVER).apply(extras)); true
    }.getOrDefault(false)

    /** Tailscale hands out addresses from 100.64.0.0/10 (CGNAT) and fd7a:115c:a1e0::/48. */
    fun isTailnetAddress(address: InetAddress): Boolean {
        val b = address.address
        return when (b.size) {
            4 -> b[0].toInt() and 0xFF == 100 && (b[1].toInt() and 0xC0) == 64
            16 -> b[0] == 0xfd.toByte() && b[1] == 0x7a.toByte() && b[2] == 0x11.toByte() && b[3] == 0x5c.toByte() && b[4] == 0xa1.toByte() && b[5] == 0xe0.toByte()
            else -> false
        }
    }
}
