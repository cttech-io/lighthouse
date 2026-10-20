package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MoonlightSourceTest {
    @Test fun parsesSunshineAppList() {
        // As Moonlight caches it for a Sunshine host.
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<root status_code="200"><App><IsHdrSupported>1</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>881448767</ID></App><App><IsHdrSupported>0</IsHdrSupported><AppTitle>Steam Big Picture</AppTitle><ID>1093255277</ID></App></root>"""
        assertEquals(
            listOf(Triple(881448767, "Desktop", true), Triple(1093255277, "Steam Big Picture", false)),
            MoonlightSource.parseAppList(xml),
        )
    }

    @Test fun unescapesTitlesAndSkipsBrokenEntries() {
        val xml = "<root><App><AppTitle>Ratchet &amp; Clank</AppTitle><ID>7</ID></App><App><AppTitle>No id</AppTitle></App></root>"
        assertEquals(listOf(Triple(7, "Ratchet & Clank", false)), MoonlightSource.parseAppList(xml))
    }
}
