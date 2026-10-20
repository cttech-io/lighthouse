package io.cttech.lighthouse.system

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * "Send from phone": a tiny one-shot web page on the local network so a long API key can be pasted on a
 * phone instead of typed with a controller. Listens only while the pop-up is open, only on the Wi-Fi
 * address, only at a random unguessable path, and stops after the first key arrives.
 * Plain HTTP: the key crosses your own Wi-Fi only, never the internet.
 */
class KeyReceiver private constructor(private val server: ServerSocket, private val token: String, private val onKey: (String) -> Unit) {
    val url: String = "http://${server.inetAddress.hostAddress}:${server.localPort}/$token"

    @Volatile private var done = false

    private val worker = thread(name = "lighthouse-key-receiver", isDaemon = true) {
        while (!done) {
            val socket = try { server.accept() } catch (_: SocketException) { break }
            runCatching { socket.use { handle(it) } }
        }
    }

    fun stop() {
        done = true
        runCatching { server.close() }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val head = readHead(input) ?: return
        val lines = head.split("\r\n")
        val (method, path) = lines.first().split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        if (path.substringBefore('?') != "/$token") return respond(socket, 404, "Not found")
        when (method) {
            "GET" -> respond(socket, 200, FORM_PAGE)
            "POST" -> {
                val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                val body = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }
                val key = String(body).split('&').firstOrNull { it.startsWith("key=") }
                    ?.let { URLDecoder.decode(it.removePrefix("key="), "UTF-8") }?.trim().orEmpty()
                if (key.isEmpty() || key.length > 200) return respond(socket, 400, page("That didn't look like a key. Go back and try again."))
                respond(socket, 200, page("✓ Sent to Lighthouse. You can close this page."))
                stop()
                onKey(key)
            }
            else -> respond(socket, 405, "Method not allowed")
        }
    }

    /** Request line + headers, up to the blank line. Null if it's too big or the client went away. */
    private fun readHead(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) return out.toString(Charsets.ISO_8859_1.name())
        }
        return null
    }

    private fun respond(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray()
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Method Not Allowed" }
        val headers = "HTTP/1.1 $code $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray()); write(bytes); flush() }
    }

    companion object {
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 2 * 1024

        /** Starts listening on the device's Wi-Fi address. Null when not on a network with an IPv4 address. */
        fun start(context: Context, onKey: (String) -> Unit): KeyReceiver? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val address = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: return null
            val server = runCatching { ServerSocket().apply { bind(InetSocketAddress(address, 0), 4) } }.getOrNull() ?: return null
            val token = ByteArray(16).also { SecureRandom().nextBytes(it) }
                .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
            return KeyReceiver(server, token, onKey)
        }

        private fun page(message: String) = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lighthouse</title><body style="font-family:system-ui;background:#111;color:#eee;padding:24px;font-size:18px">
<h2>Lighthouse</h2><p>$message</p></body>"""

        private val FORM_PAGE = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lighthouse · SteamGridDB key</title>
<body style="font-family:system-ui;background:#111;color:#eee;padding:24px;font-size:18px">
<h2>Send your SteamGridDB key</h2>
<p>Paste the API key from <a style="color:#8cf" href="https://www.steamgriddb.com/profile/preferences/api">steamgriddb.com → Preferences → API</a>, then press Send.</p>
<form method="post"><input name="key" autocomplete="off" autocapitalize="off" spellcheck="false" autofocus
 style="width:100%;font-size:18px;padding:12px;border-radius:10px;border:1px solid #555;background:#222;color:#fff;font-family:monospace">
<p><button style="font-size:18px;padding:12px 28px;border-radius:24px;border:0;background:#fff;color:#000">Send</button></p></form>
<p style="color:#999;font-size:14px">This page is served by Lighthouse on your own Wi-Fi and stops once the key arrives.</p></body>"""
    }
}
