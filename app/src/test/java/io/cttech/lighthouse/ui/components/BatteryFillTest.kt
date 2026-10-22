package io.cttech.lighthouse.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BatteryFillTest {
    private val normal = Color.White
    private val green = Color(0xFF34C759)
    private val red = Color(0xFFFF5252)
    private val amber = Color(0xFFFFB300)

    private fun fill(level: Int, plugged: Boolean = false, saver: Boolean = false, light: Boolean = false) =
        batteryFillColor(level, plugged, saver, normal, light)

    @Test fun whiteNormally() = assertEquals(normal, fill(60))

    @Test fun greenWhilePlugged() {
        assertEquals(green, fill(60, plugged = true))
        assertEquals(green, fill(100, plugged = true)) // full but still on the charger
    }

    @Test fun redBelowFifteenPercent() {
        assertEquals(red, fill(14))
        assertEquals(normal, fill(15)) // the threshold itself is not "under 15%"
        assertEquals(red, fill(0))
    }

    @Test fun amberInBatterySaver() = assertEquals(amber, fill(60, saver = true))

    @Test fun priorityIsChargingThenLowThenSaver() {
        assertEquals(green, fill(5, plugged = true, saver = true)) // recovering: green
        assertEquals(red, fill(5, saver = true)) // saver kicks in at ~15%, but low still shows red
        assertEquals(amber, fill(40, saver = true))
    }

    @Test fun unknownLevelIsNotLow() = assertEquals(normal, fill(-1))

    @Test fun deeperShadesOnTheWhiteFocusPill() {
        assertNotEquals(green, fill(60, plugged = true, light = true))
        assertNotEquals(red, fill(5, light = true))
        assertNotEquals(amber, fill(60, saver = true, light = true))
        assertEquals(normal, fill(60, light = true)) // normal state just follows the icon tint
    }
}
