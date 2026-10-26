package io.cttech.lighthouse.system

import android.app.usage.UsageEvents
import io.cttech.lighthouse.system.PlayTime.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTimeTest {
    private val game = "org.ppsspp.ppsspp"
    private fun resumed(t: Long, activity: String = "Main", pkg: String = game) = Event(t, PlayTime.RESUMED, pkg, activity)
    private fun paused(t: Long, activity: String = "Main", pkg: String = game) = Event(t, PlayTime.PAUSED, pkg, activity)
    private fun stopped(t: Long, activity: String = "Main") = Event(t, PlayTime.STOPPED, game, activity)
    private fun screenOff(t: Long) = Event(t, UsageEvents.Event.SCREEN_NON_INTERACTIVE, "android", null)

    @Test fun oneStretch() {
        assertEquals(500L, PlayTime.foregroundMs(listOf(resumed(100), paused(600)), game, 0, 1000))
    }

    @Test fun sleepInTheMiddleDoesNotCount() {
        val events = listOf(resumed(100), paused(300), resumed(700), paused(900))
        assertEquals(400L, PlayTime.foregroundMs(events, game, 0, 1000))
    }

    @Test fun otherAppsDoNotCount() {
        val events = listOf(resumed(100), paused(200), resumed(200, pkg = "com.google.android.youtube"), paused(800, pkg = "com.google.android.youtube"), resumed(800), paused(900))
        assertEquals(200L, PlayTime.foregroundMs(events, game, 0, 1000))
    }

    @Test fun stillInFrontRunsToTheEnd() {
        assertEquals(400L, PlayTime.foregroundMs(listOf(resumed(600)), game, 0, 1000))
    }

    @Test fun pausedWithoutResumeWasInFrontFromTheStart() {
        assertEquals(300L, PlayTime.foregroundMs(listOf(paused(300)), game, 0, 1000))
    }

    @Test fun activitiesInsideTheGameAreOneStretch() {
        // The emulator's menu opens a second activity before the first pauses.
        val events = listOf(resumed(100, "Menu"), resumed(400, "Emu"), paused(410, "Menu"), stopped(420, "Menu"), paused(900, "Emu"))
        assertEquals(800L, PlayTime.foregroundMs(events, game, 0, 1000))
    }

    @Test fun screenOffEndsAStretchWithoutAPause() {
        assertEquals(200L, PlayTime.foregroundMs(listOf(resumed(100), screenOff(300)), game, 0, 1000))
    }

    @Test fun noEventsIsNothing() {
        assertEquals(0L, PlayTime.foregroundMs(emptyList(), game, 0, 1000))
    }

    @Test fun formats() {
        assertEquals("under a minute", PlayTime.format(59_000))
        assertEquals("45 min", PlayTime.format(45 * 60_000L + 30_000))
        assertEquals("3 h 20 min", PlayTime.format((3 * 60 + 20) * 60_000L))
        assertEquals("12 h", PlayTime.format(12 * 3_600_000L))
    }
}
