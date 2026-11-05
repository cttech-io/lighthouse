package io.cttech.lighthouse

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.view.Display
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.cttech.lighthouse.system.DesktopMode
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cttech.lighthouse.theme.LocalLhTheme
import io.cttech.lighthouse.theme.ThemeRepository
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.ui.home.HomeScreen
import io.cttech.lighthouse.ui.home.HomeViewModel

open class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()

    /** In a desktop window (or any multi-window mode) the system draws its own title bar and taskbar: don't hide them. */
    private var windowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        syncWindowState()
        val settings = (application as LighthouseApp).settings
        setContent {
            val themes by home.themes.collectAsStateWithLifecycle()
            val theme = themes.firstOrNull { it.id == settings.themeId } ?: themes.firstOrNull() ?: ThemeRepository.Fallback
            CompositionLocalProvider(LocalAppSettings provides settings, LocalLhTheme provides theme) {
                // Full screen draws edge to edge under the (hidden) bars; a window keeps clear of its caption and taskbar.
                Box(
                    Modifier.fillMaxSize().background(theme.colors.background)
                        .then(if (windowed) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.captionBar)) else Modifier),
                ) { HomeScreen(home) }
            }
        }
    }

    /**
     * Nintendo-style layout (Settings → Controls): swap A and B before anything else sees them, so every
     * screen, pop-up and text field follows the setting without knowing about it.
     */
    // Lint mistakes this public Activity method for androidx.core's restricted one (a known false positive).
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Still in touch mode (Android may not leave it for some key sources) means nothing in the window has
        // focus, so the key would reach no screen and fall back to Back / "press focused". Take focus first;
        // the HOME root then hands it to a card and handles this same key.
        if (event.action == KeyEvent.ACTION_DOWN && window.decorView.isInTouchMode && KeyEvent.isGamepadButton(event.keyCode) ||
            event.action == KeyEvent.ACTION_DOWN && window.decorView.isInTouchMode && event.keyCode in DPAD_KEYS
        ) {
            findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.requestFocusFromTouch()
        }
        val settings = (application as LighthouseApp).settings
        val swapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            else -> null
        }
        val handled = if (!settings.swapAB || swapped == null) super.dispatchKeyEvent(event) else super.dispatchKeyEvent(
            KeyEvent(event.downTime, event.eventTime, event.action, swapped, event.repeatCount, event.metaState,
                event.deviceId, event.scanCode, event.flags, event.source),
        )
        // Android turns an unhandled Start into DPAD_CENTER (Generic.kcm fallback), which "presses" whatever is
        // focused: Start in the app drawer opened the highlighted app. Start only ever means Settings here.
        return handled || event.keyCode == KeyEvent.KEYCODE_BUTTON_START
    }

    /** singleTask: pressing Home while Lighthouse is the home app delivers a new HOME intent here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(CATEGORY_SECONDARY_HOME)) home.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        // Rescan on every return: catches changes made while we weren't watching, and a just-granted permission.
        home.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) syncWindowState()
    }

    // Declared in configChanges, so moving or resizing a window, or dragging it to another display, lands here
    // instead of recreating the activity (which would drop the open pop-up and the focused card).
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncWindowState()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        syncWindowState()
    }

    /** Remembers which display we're on (so games open there) and whether we're a window or the whole screen. */
    private fun syncWindowState() {
        DesktopMode.displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        windowed = isInMultiWindowMode
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (windowed) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                // Console-style full screen; Lighthouse draws its own clock and battery. Swipe reveals the bars briefly.
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private companion object {
        const val CATEGORY_SECONDARY_HOME = "android.intent.category.SECONDARY_HOME"
        val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}
