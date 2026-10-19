package io.cttech.lighthouse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.cttech.lighthouse.ui.home.HomeScreen
import io.cttech.lighthouse.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent { HomeScreen(home) }
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
}
