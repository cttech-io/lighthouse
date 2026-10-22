package io.cttech.lighthouse.system

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** The Send-from-phone server, over plain loopback sockets (the real one wraps the same code in TLS). */
class KeyReceiverTest {
    private val received = CopyOnWriteArrayList<String>()
    private lateinit var server: ServerSocket
    private lateinit var receiver: KeyReceiver
    private val token = "abc123"

    @Before fun start() {
        server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        receiver = KeyReceiver(server, token, "AA:BB", received::add)
    }

    @After fun stop() = receiver.stop()

    private fun connect() = Socket(InetAddress.getLoopbackAddress(), server.localPort).apply { soTimeout = 5_000 }

    /** Sends [request] and returns everything the server answers before closing. */
    private fun Socket.exchange(request: String, closeWriting: Boolean = false): String {
        getOutputStream().apply { write(request.toByteArray()); flush() }
        if (closeWriting) shutdownOutput()
        return getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    private fun post(path: String, body: String, declaredLength: Int = body.length) =
        "POST $path HTTP/1.1\r\nHost: x\r\nContent-Length: $declaredLength\r\n\r\n$body"

    @Test fun getServesTheFormAtTheSecretPath() {
        val response = connect().use { it.exchange("GET /$token HTTP/1.1\r\nHost: x\r\n\r\n") }
        assertTrue(response, response.startsWith("HTTP/1.1 200 OK"))
        assertTrue(response, response.contains("<form"))
    }

    @Test fun anyOtherPathIs404() {
        val response = connect().use { it.exchange("GET /wrong HTTP/1.1\r\nHost: x\r\n\r\n") }
        assertTrue(response, response.startsWith("HTTP/1.1 404"))
        assertTrue(received.isEmpty())
    }

    @Test fun postingToTheWrongPathNeverDeliversAKey() {
        val response = connect().use { it.exchange(post("/wrong", "key=secret")) }
        assertTrue(response, response.startsWith("HTTP/1.1 404"))
        assertTrue(received.isEmpty())
    }

    @Test fun otherMethodsAreRefused() {
        val response = connect().use { it.exchange("DELETE /$token HTTP/1.1\r\nHost: x\r\n\r\n") }
        assertTrue(response, response.startsWith("HTTP/1.1 405"))
    }

    @Test fun aPostedKeyIsDeliveredOnceAndDecoded() {
        val response = connect().use { it.exchange(post("/$token", "key=sk%2Dabc+def")) }
        assertTrue(response, response.startsWith("HTTP/1.1 200 OK"))
        assertEquals(listOf("sk-abc def"), received.toList())
    }

    @Test fun aSecondPostIsRefusedEvenOnAnAlreadyOpenConnection() {
        val first = connect()
        val second = connect()
        Thread.sleep(300) // let the server accept both before the first one finishes and closes the listener
        first.use { it.exchange(post("/$token", "key=one")) }
        val response = second.use { it.exchange(post("/$token", "key=two")) }
        assertTrue(response, response.contains("already has a key"))
        assertEquals(listOf("one"), received.toList())
    }

    @Test fun emptyOrOverlongKeysAreRejected() {
        val empty = connect().use { it.exchange(post("/$token", "key=")) }
        assertTrue(empty, empty.startsWith("HTTP/1.1 400"))
        val body = "key=" + "x".repeat(150)
        val tooLong = connect().use { it.exchange(post("/$token", "key=" + "x".repeat(300), declaredLength = 304)) }
        assertTrue(tooLong, tooLong.startsWith("HTTP/1.1 400"))
        assertTrue(received.isEmpty())
        // A key just under the limit still works afterwards.
        connect().use { it.exchange(post("/$token", body)) }
        assertEquals(listOf("x".repeat(150)), received.toList())
    }

    @Test fun theBodyIsCappedAtTwoKilobytesNoMatterWhatContentLengthSays() {
        // Claims 10 MB, sends 3000 bytes: only the first 2048 are read, so the oversize key is rejected, not buffered.
        val response = connect().use { it.exchange(post("/$token", "key=" + "y".repeat(2996), declaredLength = 10_000_000)) }
        assertTrue(response, response.startsWith("HTTP/1.1 400"))
        assertTrue(received.isEmpty())
    }

    @Test fun contentLengthLargerThanTheBodyIsRefusedNotHung() {
        // Declares 500 bytes but sends 7 and closes its side: that could be half a key, so it's refused (and the
        // server answers instead of waiting for the rest).
        val response = connect().use { it.exchange(post("/$token", "key=abc", declaredLength = 500), closeWriting = true) }
        assertTrue(response, response.startsWith("HTTP/1.1 400"))
        assertTrue(received.isEmpty())
    }

    @Test fun contentLengthSmallerThanTheBodyReadsOnlyWhatItDeclared() {
        val response = connect().use { it.exchange(post("/$token", "key=abcdef", declaredLength = 7)) }
        assertTrue(response, response.startsWith("HTTP/1.1 200 OK"))
        assertEquals(listOf("abc"), received.toList())
    }

    @Test fun anOversizedHeaderIsDropped() {
        // The server hangs up without answering, so the client sees an empty reply or a reset: never a 200.
        val response = runCatching { connect().use { it.exchange("GET /$token HTTP/1.1\r\nX-Pad: " + "z".repeat(9_000) + "\r\n\r\n") } }
        assertTrue(response.toString(), response.getOrDefault("").isEmpty())
        assertTrue(received.isEmpty())
    }
}
