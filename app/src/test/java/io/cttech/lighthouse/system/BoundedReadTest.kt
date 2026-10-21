package io.cttech.lighthouse.system

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedReadTest {
    @Test fun streamAtLimitIsRead() {
        val data = ByteArray(100) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readBounded(100))
    }

    @Test fun streamOverLimitIsRejected() {
        assertNull(ByteArrayInputStream(ByteArray(101)).readBounded(100))
    }

    @Test fun idFileIsTrimmed() {
        val f = File.createTempFile("game-id", ".steam").apply { writeText("12345\n"); deleteOnExit() }
        assertEquals("12345", f.readIdText())
    }

    @Test fun oversizedIdFileIsRejected() {
        val f = File.createTempFile("game-id", ".steam").apply { writeText("1".repeat(65)); deleteOnExit() }
        assertNull(f.readIdText())
    }

    @Test fun oversizedFileIsRejectedWithoutReading() {
        val f = File.createTempFile("img", ".png").apply { writeBytes(ByteArray(11)); deleteOnExit() }
        assertNull(f.readBounded(10))
    }
}
