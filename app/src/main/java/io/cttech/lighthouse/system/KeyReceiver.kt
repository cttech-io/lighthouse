package io.cttech.lighthouse.system

import android.content.Context
import android.net.ConnectivityManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread

/**
 * "Send from phone": a tiny one-shot web page on the local network so a long API key can be pasted on a
 * phone instead of typed with a controller. Listens only while the pop-up is open, only on the Wi-Fi
 * address, only at a random unguessable path, and stops after the first key arrives.
 *
 * HTTPS with a self-signed certificate made in the Android Keystore (the private key never leaves it).
 * Phones warn once that it isn't trusted, which is expected; the pop-up shows the fingerprint to compare.
 * HTTPS also makes the page a secure context, so its Paste button can read the phone's clipboard.
 */
class KeyReceiver internal constructor(
    private val server: ServerSocket,
    private val token: String,
    /** First bytes of the certificate's SHA-256 fingerprint, e.g. "3F:A2:…", to compare with the phone. */
    val fingerprint: String,
    private val onKey: (String) -> Unit,
) {
    val url: String = "https://${server.inetAddress.hostAddress}:${server.localPort}/$token"

    @Volatile private var done = false
    private val active = AtomicInteger(0)
    private val sent = AtomicBoolean(false)

    // One thread per connection: browsers open spare connections in advance and leave them idle, which
    // would hold up the real request if connections were handled one at a time.
    private val worker = thread(name = "lighthouse-key-receiver", isDaemon = true) {
        while (!done) {
            val socket = try { server.accept() } catch (_: SocketException) { break }
            if (active.incrementAndGet() > MAX_CONNECTIONS) { active.decrementAndGet(); runCatching { socket.close() }; continue }
            thread(isDaemon = true) {
                try { runCatching { socket.use { handle(it) } } } finally { active.decrementAndGet() }
            }
        }
    }

    fun stop() {
        done = true
        runCatching { server.close() }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000 // includes the TLS handshake, which waits on the person tapping through the warning
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
                val body = ByteArray(length)
                var read = 0
                while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
                // The client sent less than it promised: that could be half a key, so don't accept it.
                if (read < length) return respond(socket, 400, page("That didn't look like a key. Go back and try again."))
                val key = String(body).split('&').firstOrNull { it.startsWith("key=") }
                    ?.let { URLDecoder.decode(it.removePrefix("key="), "UTF-8") }?.trim().orEmpty()
                if (key.isEmpty() || key.length > 200) return respond(socket, 400, page("That didn't look like a key. Go back and try again."))
                if (!sent.compareAndSet(false, true)) return respond(socket, 200, page("Lighthouse already has a key from this page."))
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
        private const val MAX_CONNECTIONS = 16
        private const val KEY_ALIAS = "lighthouse-key-receiver"

        /**
         * Starts listening on the device's Wi-Fi address. Null when not on a network with an IPv4 address,
         * or if the certificate can't be made. Blocking (keystore work): call off the main thread.
         */
        fun start(context: Context, onKey: (String) -> Unit): KeyReceiver? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val address = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: return null
            val keyStore = runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }.getOrNull() ?: return null
            if (!keyStore.containsAlias(KEY_ALIAS)) runCatching { createKey() }.getOrNull() ?: return null
            val ssl = runCatching {
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, null) }
                SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            }.getOrNull() ?: return null
            val server = runCatching { ssl.serverSocketFactory.createServerSocket().apply { bind(InetSocketAddress(address, 0), 16) } }.getOrNull() ?: return null
            val cert = keyStore.getCertificate(KEY_ALIAS)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .take(6).joinToString(":") { "%02X".format(it) }
            val token = ByteArray(16).also { SecureRandom().nextBytes(it) }
                .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
            return KeyReceiver(server, token, fingerprint, onKey)
        }

        /** EC P-256 key in the Android Keystore; the keystore makes the self-signed certificate for it. */
        private fun createKey() {
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                // TLS signs pre-hashed data, so NONE is needed alongside the usual digests.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(X500Principal("CN=Lighthouse"))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - 24L * 3600 * 1000))
                .setCertificateNotAfter(Date(now + 10L * 365 * 24 * 3600 * 1000))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }

        private fun page(message: String) = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lighthouse</title><body style="font-family:system-ui;background:#111;color:#eee;padding:24px;font-size:18px">
<h2>Lighthouse</h2><p>$message</p></body>"""

        private val FORM_PAGE = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lighthouse · SteamGridDB key</title>
<body style="font-family:system-ui;background:#111;color:#eee;padding:24px;font-size:18px">
<h2>Send your SteamGridDB key</h2>
<p>Copy your API key from <a style="color:#8cf" href="https://www.steamgriddb.com/profile/preferences/api" target="_blank">steamgriddb.com → Preferences → API</a>, then come back here.</p>
<form method="post" id="f">
<button type="button" id="paste" style="display:none;font-size:18px;padding:12px 28px;border-radius:24px;border:0;background:#8cf;color:#000;margin-bottom:12px">Paste key</button>
<input id="key" name="key" type="text" placeholder="…or paste it here" autocomplete="off" autocapitalize="none" autocorrect="off" spellcheck="false"
 style="box-sizing:border-box;width:100%;font-size:18px;padding:16px;border-radius:10px;border:1px solid #555;background:#222;color:#fff;font-family:monospace">
<p><button style="font-size:18px;padding:12px 28px;border-radius:24px;border:0;background:#fff;color:#000">Send</button></p></form>
<p id="msg" style="color:#f99"></p>
<p style="color:#999;font-size:14px">This page is served by Lighthouse on your own Wi-Fi and stops once the key arrives.</p>
<script>
const b = document.getElementById('paste'), k = document.getElementById('key'), m = document.getElementById('msg');
if (navigator.clipboard && navigator.clipboard.readText) {
  b.style.display = 'block';
  b.onclick = async () => {
    try {
      const t = (await navigator.clipboard.readText()).trim();
      if (!t) { m.textContent = 'The clipboard is empty: copy the key first.'; return; }
      k.value = t; m.textContent = '';
    } catch (e) { m.textContent = 'Couldn\'t read the clipboard. Long-press the box and choose Paste instead.'; }
  };
}
</script></body>"""
    }
}
