package io.cttech.lighthouse.covers

import android.util.Log
import io.cttech.lighthouse.library.TitleCleaner
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
        if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
    } finally {
        c.disconnect()
    }
}.onFailure { Log.w(TAG, "GET failed: ${url.substringBefore('?')}: ${it.message}") }.getOrNull()

/** SteamGridDB 600×900 grids. Needs the user's own API key. */
class SteamGridDb(private val apiKey: String) {
    private val base = "https://www.steamgriddb.com/api/v2"
    private val auth = mapOf("Authorization" to "Bearer $apiKey")

    fun forSteamApp(appId: Int): ByteArray? = firstGrid("$base/grids/steam/$appId")

    data class SearchHit(val id: Int, val name: String, val year: Int?)
    data class Grid(val url: String, val thumb: String, val style: String)

    /** Game search for the cover picker. */
    fun search(term: String): List<SearchHit> {
        val json = httpGet("$base/search/autocomplete/${enc(term)}", auth) ?: return emptyList()
        val data = JSONObject(String(json)).optJSONArray("data") ?: return emptyList()
        return List(data.length()) { i ->
            val o = data.getJSONObject(i)
            val year = o.optLong("release_date", 0).takeIf { it > 0 }?.let {
                java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneOffset.UTC).year
            }
            SearchHit(o.getInt("id"), o.getString("name"), year)
        }
    }

    /** All static 600×900 grids for a SteamGridDB game id (or a Steam app id), best-scored first. */
    fun grids(gameId: Int? = null, steamAppId: Int? = null): List<Grid> {
        val endpoint = when {
            steamAppId != null -> "$base/grids/steam/$steamAppId"
            gameId != null -> "$base/grids/game/$gameId"
            else -> return emptyList()
        }
        val json = httpGet("$endpoint?dimensions=600x900&types=static&nsfw=false&humor=false", auth) ?: return emptyList()
        val data = JSONObject(String(json)).optJSONArray("data") ?: return emptyList()
        return List(data.length()) { i ->
            val o = data.getJSONObject(i)
            Grid(o.getString("url"), o.optString("thumb", o.getString("url")), o.optString("style"))
        }
    }

    /** One cheap authenticated call; false for a wrong key or no network. */
    fun isValid(): Boolean = httpGet("$base/search/autocomplete/zelda", auth)?.let { JSONObject(String(it)).optBoolean("success") } == true

    /** Title search; only accepts a result whose name actually matches, to avoid wrong art (e.g. ROM hacks). */
    fun forTitle(title: String): ByteArray? {
        val json = httpGet("$base/search/autocomplete/${enc(title)}", auth) ?: return null
        val results = JSONObject(String(json)).optJSONArray("data") ?: return null
        val want = TitleCleaner.sortKey(title)
        for (i in 0 until minOf(results.length(), 5)) {
            val r = results.getJSONObject(i)
            val name = TitleCleaner.sortKey(r.getString("name").replace(Regex("""\s*\((Program|Emulator)\)"""), ""))
            if (name == want || name.startsWith(want) || want.startsWith(name) && name.length >= want.length * 0.7) {
                return firstGrid("$base/grids/game/${r.getInt("id")}")
            }
        }
        Log.i(TAG, "SteamGridDB: no confident match for '$title'")
        return null
    }

    private fun firstGrid(endpoint: String): ByteArray? {
        val json = httpGet("$endpoint?dimensions=600x900&types=static&nsfw=false&humor=false", auth) ?: return null
        val data = JSONObject(String(json)).optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        return httpGet(data.getJSONObject(0).getString("url"))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** libretro-thumbnails box art, matched by the original (No-Intro / Redump) file name. No key needed. */
object LibretroThumbnails {
    private val systems = mapOf(
        "gba" to listOf("Nintendo - Game Boy Advance"),
        "gbc" to listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy"),
        "n64" to listOf("Nintendo - Nintendo 64"),
        "ps2" to listOf("Sony - PlayStation 2"),
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
