package io.cttech.lighthouse.system

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.Display

/**
 * Android's desktop windowing (Android 16+, Samsung DeX, a phone on an external display): Lighthouse can be a
 * resizable window, or the home screen of a second display. Games and apps then have to open where the player is
 * looking, not on the phone's own screen.
 */
object DesktopMode {
    /** The display Lighthouse is showing on; kept up to date by [io.cttech.lighthouse.MainActivity]. */
    @Volatile
    var displayId: Int = Display.DEFAULT_DISPLAY

    /** Launch options that keep a started app on Lighthouse's display, or null on the main display (the default). */
    fun launchOptions(): android.os.Bundle? =
        if (displayId == Display.DEFAULT_DISPLAY) null else ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
}

/** startActivity that opens the app on the display Lighthouse is on. */
fun Context.startActivityOnHomeDisplay(intent: Intent) = startActivity(intent, DesktopMode.launchOptions())
