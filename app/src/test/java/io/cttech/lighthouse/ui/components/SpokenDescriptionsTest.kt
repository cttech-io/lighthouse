package io.cttech.lighthouse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack says for the status icons and button hints. */
class SpokenDescriptionsTest {
    @Test fun wifi() {
        assertEquals("Wi-Fi off", wifiDescription(enabled = false, connected = false, level = 0))
        assertEquals("Wi-Fi on, not connected", wifiDescription(enabled = true, connected = false, level = 0))
        assertEquals("Wi-Fi connected, signal 3 of 4", wifiDescription(enabled = true, connected = true, level = 3))
        assertEquals("Wi-Fi connected, signal 1 of 4", wifiDescription(enabled = true, connected = true, level = 0)) // as drawn
    }

    @Test fun network() {
        assertEquals("Wi-Fi connected, signal 3 of 4", networkDescription(true, true, 3, ethernet = false, tailscale = false))
        assertEquals("Ethernet connected", networkDescription(true, true, 3, ethernet = true, tailscale = false))
        assertEquals("Ethernet connected, Tailscale on", networkDescription(false, false, 0, ethernet = true, tailscale = true))
        assertEquals("Wi-Fi on, not connected, Tailscale on", networkDescription(true, false, 0, ethernet = false, tailscale = true))
    }

    @Test fun bluetooth() {
        assertEquals("Bluetooth off", bluetoothDescription(enabled = false, connected = false))
        assertEquals("Bluetooth on", bluetoothDescription(enabled = true, connected = false))
        assertEquals("Bluetooth on, device connected", bluetoothDescription(enabled = true, connected = true))
    }

    @Test fun volume() {
        assertEquals("Volume 50 percent", volumeDescription(level = 8, max = 16, muted = false))
        assertEquals("Volume muted", volumeDescription(level = 8, max = 16, muted = true))
        assertEquals("Volume muted", volumeDescription(level = 0, max = 16, muted = false))
    }

    @Test fun battery() {
        assertEquals("Battery 80 percent", batteryDescription(80, plugged = false, powerSave = false))
        assertEquals("Battery 80 percent, charging", batteryDescription(80, plugged = true, powerSave = true))
        assertEquals("Battery 12 percent, battery saver on", batteryDescription(12, plugged = false, powerSave = true))
    }

    @Test fun hints() {
        assertEquals("Select, A button", hintDescription("A", "Select"))
        assertEquals("Section, L1 or R1 button", hintDescription("L1 R1", "Section"))
        assertEquals("Settings, Start button", hintDescription("☰", "Settings"))
        assertEquals("Options, Select button", hintDescription("⧉", "Options"))
    }
}
