package io.cttech.lighthouse.system

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small user preferences. Backed by SharedPreferences; exposed as Compose state so the UI reacts instantly. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("lighthouse_settings", Context.MODE_PRIVATE)

    var hapticsEnabled by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set

    fun setHaptics(enabled: Boolean) {
        hapticsEnabled = enabled
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    private companion object { const val KEY_HAPTICS = "haptics_enabled" }
}

val LocalAppSettings = compositionLocalOf<AppSettings> { error("AppSettings not provided") }
