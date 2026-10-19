package io.cttech.lighthouse.theme

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File

data class ThemeColors(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
    val textMuted: Color,
    val focus: Color,
)

/**
 * A Lighthouse theme: colours, cover style and backgrounds per view. Background keys are view keys:
 * "home", "platform:<id>", with "default" as the fallback. Values are image models Coil can load
 * (bundled: file:///android_asset/…, user themes: absolute file paths). See docs/THEMES.md.
 */
data class LhTheme(
    val id: String,
    val name: String,
    val author: String,
    val colors: ThemeColors,
    val coverCorner: Dp,
    val focusScale: Float,
    /** 0..1 black overlay over backgrounds, for readability. */
    val scrim: Float,
    val backgrounds: Map<String, String>,
    val userTheme: Boolean,
) {
    fun backgroundFor(viewKey: String): String? = backgrounds[viewKey] ?: backgrounds["default"]
}

val LocalLhTheme = staticCompositionLocalOf { ThemeRepository.Fallback }

/** Shorthand for the current theme inside composables. */
object Lh {
    val theme: LhTheme @Composable @ReadOnlyComposable get() = LocalLhTheme.current
    val colors: ThemeColors @Composable @ReadOnlyComposable get() = LocalLhTheme.current.colors
}

/** Loads bundled themes (assets/themes/<id>/) and the user's (Lighthouse/themes/<id>/ on any storage volume). */
class ThemeRepository(private val context: Context) {

    fun loadAll(storageRoots: List<File>): List<LhTheme> {
        val bundled = context.assets.list("themes").orEmpty().sorted().mapNotNull { id ->
            runCatching {
                val json = context.assets.open("themes/$id/theme.json").bufferedReader().use { it.readText() }
                parse(json, base = "file:///android_asset/themes/$id/", user = false)
            }.onFailure { Log.w(TAG, "bad bundled theme $id", it) }.getOrNull()
        }
        val user = storageRoots.flatMap { root ->
            File(root, "Lighthouse/themes").listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
                val file = File(dir, "theme.json").takeIf { it.isFile } ?: return@mapNotNull null
                runCatching { parse(file.readText(), base = dir.absolutePath + "/", user = true) }
                    .onFailure { Log.w(TAG, "bad user theme ${dir.name}", it) }.getOrNull()
            }
        }
        // Bundled "lighthouse" first, then the rest alphabetically; a user theme with the same id overrides.
        return (bundled + user).associateBy { it.id }.values.sortedWith(compareBy({ it.id != "lighthouse" }, { it.name.lowercase() }))
    }

    private fun parse(json: String, base: String, user: Boolean): LhTheme {
        val o = JSONObject(json)
        val c = o.getJSONObject("colors")
        val cover = o.optJSONObject("cover")
        val bg = o.optJSONObject("backgrounds")
        return LhTheme(
            id = o.getString("id"),
            name = o.optString("name", o.getString("id")),
            author = o.optString("author", ""),
            colors = ThemeColors(
                background = c.color("background", Fallback.colors.background),
                surface = c.color("surface", Fallback.colors.surface),
                accent = c.color("accent", Fallback.colors.accent),
                text = c.color("text", Fallback.colors.text),
                textMuted = c.color("textMuted", Fallback.colors.textMuted),
                focus = c.color("focus", Fallback.colors.focus),
            ),
            coverCorner = (cover?.optDouble("cornerRadius", 10.0) ?: 10.0).toFloat().coerceIn(0f, 40f).dp,
            focusScale = (cover?.optDouble("focusScale", 1.06) ?: 1.06).toFloat().coerceIn(1f, 1.2f),
            scrim = o.optDouble("scrim", 0.0).toFloat().coerceIn(0f, 0.9f),
            backgrounds = bg?.keys()?.asSequence()?.associateWith { k -> base + bg.getString(k) } ?: emptyMap(),
            userTheme = user,
        )
    }

    private fun JSONObject.color(name: String, fallback: Color): Color =
        optString(name).takeIf { it.isNotEmpty() }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback

    companion object {
        private const val TAG = "LighthouseThemes"

        /** Built-in look, used before themes load and when a theme is missing. */
        val Fallback = LhTheme(
            id = "lighthouse",
            name = "Lighthouse",
            author = "Lighthouse",
            colors = ThemeColors(
                background = Color(0xFF05060A),
                surface = Color(0xFF151A26),
                accent = Color(0xFF6FA8FF),
                text = Color.White,
                textMuted = Color(0xFFB7C1D6),
                focus = Color.White,
            ),
            coverCorner = 10.dp,
            focusScale = 1.06f,
            scrim = 0f,
            backgrounds = emptyMap(),
            userTheme = false,
        )
    }
}
