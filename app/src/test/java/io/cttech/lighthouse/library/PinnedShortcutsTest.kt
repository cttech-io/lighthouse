package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedShortcutsTest {
    private val uuid = "0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9"

    @Test fun moonlightPinBecomesTheSameEntryAsTheRootRead() {
        val app = PinnedShortcuts.asMoonlightApp(PinnedShortcuts.Pinned("com.limelight", "${uuid}1093255277", "Steam Big Picture (Gaming PC)"))!!
        assertEquals("$uuid/1093255277", app.locator)
        assertEquals("Steam Big Picture", app.title)
        assertEquals("Gaming PC", app.hostName)
    }

    @Test fun otherShortcutsStayShortcuts() {
        assertNull(PinnedShortcuts.asMoonlightApp(PinnedShortcuts.Pinned("com.android.chrome", "${uuid}1", "Site")))
        // Moonlight's per-PC shortcut (id = uuid only) isn't a game.
        assertNull(PinnedShortcuts.asMoonlightApp(PinnedShortcuts.Pinned("com.limelight", uuid, "Gaming PC")))
    }
}
