package io.cttech.lighthouse.launch

import android.content.Context
import org.json.JSONObject

enum class RomDelivery { CONTENT, EXTRAS, STEAM_APP_ID }

/** One way of starting games: an emulator (or PC runner) and how it wants the game handed over. */
data class LaunchProfile(
    val id: String,
    val label: String,
    val platforms: Set<String>,
    /** Alternatives (e.g. RetroArch's 64-bit and plain builds); the first installed one is used. */
    val packages: List<String>,
    val activity: String?,
    val action: String,
    val rom: RomDelivery,
    val mime: String?,
    val needsStorage: Boolean,
    /** Libretro core path to check before launching (needs root to see inside the emulator's data). */
    val core: String?,
    val extras: Map<String, String>,
)

/** Reads assets/launch_profiles.json. Adding an emulator is a JSON edit, not a code change. */
object LaunchProfiles {
    @Volatile private var cached: List<LaunchProfile>? = null

    fun all(context: Context): List<LaunchProfile> = cached ?: load(context).also { cached = it }

    fun forPlatform(context: Context, platformId: String): List<LaunchProfile> =
        all(context).filter { platformId in it.platforms }

    private fun load(context: Context): List<LaunchProfile> {
        val root = JSONObject(context.assets.open("launch_profiles.json").bufferedReader().use { it.readText() })
        val arr = root.getJSONArray("profiles")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val extras = o.optJSONObject("extras")
            LaunchProfile(
                id = o.getString("id"),
                label = o.getString("label"),
                platforms = o.getJSONArray("platforms").let { a -> List(a.length()) { a.getString(it) }.toSet() },
                packages = o.getJSONArray("packages").let { a -> List(a.length()) { a.getString(it) } },
                activity = o.optString("activity").ifEmpty { null },
                action = o.getString("action"),
                rom = when (o.getString("rom")) {
                    "content" -> RomDelivery.CONTENT
                    "extras" -> RomDelivery.EXTRAS
                    "steamAppId" -> RomDelivery.STEAM_APP_ID
                    else -> error("Unknown rom delivery in profile ${o.getString("id")}")
                },
                mime = o.optString("mime").ifEmpty { null },
                needsStorage = o.optBoolean("needsStorage", false),
                core = o.optString("core").ifEmpty { null },
                extras = extras?.keys()?.asSequence()?.associateWith { extras.getString(it) } ?: emptyMap(),
            )
        }
    }
}
