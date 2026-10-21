package io.cttech.lighthouse.covers

import android.util.Log
import io.cttech.lighthouse.library.TitleCleaner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import io.cttech.lighthouse.system.readBounded

private const val TAG = "LighthouseCovers"
private const val UA = "Lighthouse/0.1 (+https://github.com/cttech-io/lighthouse)"

/** Tiny HTTP GET; null on any failure (covers are best-effort). */
internal fun httpGet(url: String, headers: Map<String, String> = emptyMap()): ByteArray? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 15_000
    c.setRequestProperty("User-Agent", UA)
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    try {
        if (c.responseCode != 200) null else c.inputStream.use { it.readBounded() }
    } finally {
        c.disconnect()
    }
}.onFailure { Log.w(TAG, "GET failed: ${url.substringBefore('?')}: ${it.message}") }.getOrNull()

/**
 * SteamGridDB 600×900 grids. Needs the user's own API key. [get] is the HTTP call (swapped in tests).
 * Every response goes through [data]: anything that isn't the expected JSON (a captive portal, a proxy page,
 * an API change) counts as "nothing found" rather than an exception, so it can never crash the app.
 */
class SteamGridDb(
    private val apiKey: String,
    private val get: (url: String, headers: Map<String, String>) -> ByteArray? = ::httpGet,
) {
    private val base = "https://www.steamgriddb.com/api/v2"
    private val auth = mapOf("Authorization" to "Bearer $apiKey")

    fun forSteamApp(appId: Int): ByteArray? = firstGrid("$base/grids/steam/$appId")

    data class SearchHit(val id: Int, val name: String, val year: Int?)
    data class Grid(val url: String, val thumb: String, val style: String)

    /** Game search for the cover picker. */
    fun search(term: String): List<SearchHit> =
        objects(data("$base/search/autocomplete/${enc(term)}")).mapNotNull { o ->
            val id = o.optInt("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val year = o.optLong("release_date", 0).takeIf { it > 0 }?.let {
                java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneOffset.UTC).year
            }
            SearchHit(id, name, year)
        }

    /** All static 600×900 grids for a SteamGridDB game id (or a Steam app id), best-scored first. */
    fun grids(gameId: Int? = null, steamAppId: Int? = null): List<Grid> {
        val endpoint = when {
            steamAppId != null -> "$base/grids/steam/$steamAppId"
            gameId != null -> "$base/grids/game/$gameId"
            else -> return emptyList()
        }
        return objects(data("$endpoint$GRID_QUERY")).mapNotNull { o ->
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Grid(url, o.optString("thumb").ifBlank { url }, o.optString("style"))
        }
    }

    /** One cheap authenticated call; false for a wrong key or no network. */
    fun isValid(): Boolean =
        get("$base/search/autocomplete/zelda", auth)?.let { runCatching { JSONObject(String(it)).optBoolean("success") }.getOrNull() } == true

    /** Title search; only accepts a result whose name actually matches, to avoid wrong art (e.g. ROM hacks). */
    fun forTitle(title: String): ByteArray? {
        val want = TitleCleaner.sortKey(title)
        for (r in objects(data("$base/search/autocomplete/${enc(title)}")).take(5)) {
            val id = r.optInt("id", -1).takeIf { it > 0 } ?: continue
            val name = TitleCleaner.sortKey(r.optString("name").replace(Regex("""\s*\((Program|Emulator)\)"""), ""))
            if (name.isEmpty()) continue
            if (name == want || name.startsWith(want) || want.startsWith(name) && name.length >= want.length * 0.7) {
                return firstGrid("$base/grids/game/$id")
            }
        }
        return null
    }

    private fun firstGrid(endpoint: String): ByteArray? =
        objects(data("$endpoint$GRID_QUERY")).firstNotNullOfOrNull { o -> o.optString("url").takeIf { it.isNotBlank() } }
            ?.let { get(it, emptyMap()) }

    /** The response's "data" array; null when the request failed or the body isn't the expected JSON. */
    private fun data(url: String): JSONArray? =
        get(url, auth)?.let { body -> runCatching { JSONObject(String(body)).optJSONArray("data") }.getOrNull() }

    private fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else List(array.length()) { array.optJSONObject(it) }.filterNotNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val GRID_QUERY = "?dimensions=600x900&types=static&nsfw=false&humor=false"
    }
}

/** libretro-thumbnails box art, matched by the original (No-Intro / Redump) file name. No key needed. */
object LibretroThumbnails {
    private val systems = mapOf(
        "gba" to listOf("Nintendo - Game Boy Advance"),
        "gbc" to listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy"),
        "n64" to listOf("Nintendo - Nintendo 64"),
        "ps2" to listOf("Sony - PlayStation 2"),
        "psp" to listOf("Sony - PlayStation Portable"),
    )
    private val unsafe = Regex("""[&*/:`<>?\\|"]""")

    fun forRom(platformId: String, romPath: String): ByteArray? {
        val names = systems[platformId] ?: return null
        val base = File(romPath).nameWithoutExtension
        // Exact name first, then without version/revision tags, then with only the first tag group.
        val candidates = linkedSetOf(
            base,
            base.replace(Regex("""\s*\((v|Rev )[^)]*\)"""), ""),
            Regex("""^([^(\[]+\([^)]*\))""").find(base)?.value ?: base,
        )
        for (system in names) for (name in candidates) {
            val url = "https://thumbnails.libretro.com/${pathEnc(system)}/Named_Boxarts/${pathEnc(name.replace(unsafe, "_"))}.png"
            httpGet(url)?.let { return it }
        }
        return null
    }

    private fun pathEnc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** User-supplied covers: an image beside the ROM, or Lighthouse/covers/<platform>/<name>.<ext> on any volume. */
object ManualCovers {
    private val exts = listOf("png", "jpg", "jpeg", "webp")

    fun find(platformId: String, title: String, locator: String, isApp: Boolean, roots: List<File>): File? {
        if (!isApp) {
            val rom = File(locator)
            exts.firstNotNullOfOrNull { e -> File(rom.parentFile, "${rom.nameWithoutExtension}.$e").takeIf { it.isFile } }?.let { return it }
        }
        val names = buildList {
            add(title); add(title.replace(":", " -")); add(title.replace(":", ""))
            if (isApp) add(locator) else add(File(locator).nameWithoutExtension)
        }
        for (root in roots) {
            val dir = File(root, "Lighthouse/covers/$platformId")
            if (!dir.isDirectory) continue
            for (n in names) for (e in exts) File(dir, "$n.$e").takeIf { it.isFile }?.let { return it }
        }
        return null
    }
}
