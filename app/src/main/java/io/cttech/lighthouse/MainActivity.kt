package io.cttech.lighthouse

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cttech.lighthouse.theme.LocalLhTheme
import io.cttech.lighthouse.theme.ThemeRepository
import io.cttech.lighthouse.system.LocalAppSettings
import io.cttech.lighthouse.ui.home.HomeScreen
import io.cttech.lighthouse.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val settings = (application as LighthouseApp).settings
        setContent {
            val themes by home.themes.collectAsStateWithLifecycle()
            val theme = themes.firstOrNull { it.id == settings.themeId } ?: themes.firstOrNull() ?: ThemeRepository.Fallback
            CompositionLocalProvider(LocalAppSettings provides settings, LocalLhTheme provides theme) { HomeScreen(home) }
        }
    }

    /**
     * Nintendo-style layout (Settings → Controls): swap A and B before anything else sees them, so every
     * screen, pop-up and text field follows the setting without knowing about it.
     */
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
        if (intent.hasCategory(Intent.CATEGORY_HOME)) home.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        // Rescan on every return: catches changes made while we weren't watching, and a just-granted permission.
        home.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Console-style full screen; Lighthouse draws its own clock and battery. Swipe reveals the bars briefly. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}
