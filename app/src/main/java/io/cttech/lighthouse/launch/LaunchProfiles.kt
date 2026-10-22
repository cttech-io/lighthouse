package io.cttech.lighthouse.launch

import android.content.Context
import org.json.JSONObject

/** How a profile hands over the game: a FileProvider link, a document-shaped link (see [RomDocumentProvider]), intent extras, or a Steam ID. */
enum class RomDelivery { CONTENT, DOCUMENT, EXTRAS, STEAM_APP_ID }

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
    /**
     * Libretro core paths to check before launching (needs root to see inside the emulator's data), as alternatives:
     * the first installed one is used and fills `{core}` in the extras.
     */
    val cores: List<String>,
    /** String values (placeholders expanded) or JSON booleans, passed as boolean extras. */
    val extras: Map<String, Any>,
    /** Where to get the emulator; offered when it isn't installed. */
    val website: String? = null,
) {
    /** For people: "RetroArch · mGBA" reads like two apps, so it's "RetroArch (mGBA core)". */
    val displayName: String get() = label.split(" · ").let { if (it.size == 2) "${it[0]} (${it[1]} core)" else label }
}

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
                    "document" -> RomDelivery.DOCUMENT
                    "extras" -> RomDelivery.EXTRAS
                    "steamAppId" -> RomDelivery.STEAM_APP_ID
                    else -> error("Unknown rom delivery in profile ${o.getString("id")}")
                },
                mime = o.optString("mime").ifEmpty { null },
                needsStorage = o.optBoolean("needsStorage", false),
                cores = o.optJSONArray("core")?.let { a -> List(a.length()) { a.getString(it) } }
                    ?: listOfNotNull(o.optString("core").ifEmpty { null }),
                extras = extras?.keys()?.asSequence()?.associateWith { k -> extras.get(k).let { v -> v as? Boolean ?: v.toString() } } ?: emptyMap(),
                website = o.optString("website").ifEmpty { null },
            )
        }
    }
}
