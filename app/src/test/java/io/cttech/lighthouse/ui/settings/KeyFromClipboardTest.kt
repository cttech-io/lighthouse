package io.cttech.lighthouse.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyFromClipboardTest {
    @Test fun aPlainKeyIsAccepted() = assertEquals("0123456789abcdef0123456789abcdef", keyFromClipboard("0123456789abcdef0123456789abcdef"))

    @Test fun surroundingWhitespaceIsTrimmed() = assertEquals("abc123", keyFromClipboard("  abc123\n"))

    @Test fun emptyOrBlankIsRejected() {
        assertNull(keyFromClipboard(""))
        assertNull(keyFromClipboard("  \n "))
    }

    @Test fun textWithSpacesIsRejected() = assertNull(keyFromClipboard("my api key is abc"))

    @Test fun aLongPasteIsRejected() {
        assertEquals("a".repeat(200), keyFromClipboard("a".repeat(200)))
        assertNull(keyFromClipboard("a".repeat(201)))
    }
}
