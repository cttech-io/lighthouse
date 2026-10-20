package io.cttech.lighthouse.covers

import android.util.Log
import io.cttech.lighthouse.data.db.CoverEntity
import io.cttech.lighthouse.data.db.GameEntity
import io.cttech.lighthouse.data.db.GameKind
import io.cttech.lighthouse.data.db.LibraryDao
import io.cttech.lighthouse.system.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Progress of a cover job, shown in HOME's status strip. */
data class CoverJob(val label: String, val done: Int, val total: Int, val found: Int, val running: Boolean)

/**
 * Finds and stores cover art. Order of preference: the user's own images (always, offline, on every scan),
 * then — only when asked — SteamGridDB (Steam app id for shortcuts, else title) and libretro thumbnails.
 */
class CoverService(
    private val context: android.content.Context,
    private val dao: LibraryDao,
    private val store: CoverStore,
    private val settings: AppSettings,
    private val cacheDir: File,
) {
    private val _job = MutableStateFlow<CoverJob?>(null)
    val job: StateFlow<CoverJob?> = _job

    private val _offer = MutableStateFlow<List<String>>(emptyList())
    /** Games (keys) that arrived without local artwork and that the user hasn't been asked about yet. */
    val offer: StateFlow<List<String>> = _offer

    /**
     * After each scan (and after local covers were applied): games still without any cover are offered for an
     * online search once (Ask), fetched straight away (Always), or left alone (Never). Each game is only
     * offered or tried once; "Find missing covers" in Settings retries everything.
     */
    suspend fun checkNewGames() = withContext(Dispatchers.IO) {
        if (settings.coverAuto == io.cttech.lighthouse.system.CoverAutoMode.Never) { _offer.value = emptyList(); return@withContext }
        val have = dao.covers().map { it.gameKey }.toSet()
        val offered = settings.coverOffered()
        val fresh = dao.allGames().map { it.key }.filter { it !in have && it !in offered }
        if (fresh.isEmpty() || _job.value?.running == true || !hasNetwork()) { _offer.value = emptyList(); return@withContext }
        when (settings.coverAuto) {
            io.cttech.lighthouse.system.CoverAutoMode.Always -> { settings.markCoverOffered(fresh); findMissing(fresh.toSet()) }
            else -> _offer.value = fresh
        }
    }

    /** Moonlight games get the box art Moonlight already has from the PC; picks and removals stand. */
    suspend fun applyMoonlight() = withContext(Dispatchers.IO) {
        val have = dao.covers().map { it.gameKey }.toSet()
        dao.allGames().filter { it.platformId == io.cttech.lighthouse.library.PlatformCatalog.MOONLIGHT && it.key !in have }.forEach { g ->
            val bytes = io.cttech.lighthouse.library.MoonlightSource.boxArt(context, g.locator) ?: return@forEach
            store.save(g.key, bytes)?.let { dao.putCover(CoverEntity(g.key, it.absolutePath, SOURCE_MOONLIGHT, System.currentTimeMillis())) }
        }
    }

    /** The user answered the offer: [fetch] runs the search for those games; either way they're not asked again. */
    suspend fun answerOffer(fetch: Boolean) {
        val keys = _offer.value
        _offer.value = emptyList()
        settings.markCoverOffered(keys)
        if (fetch) findMissing(keys.toSet())
    }

    /** Drops every downloaded cover (SteamGridDB, libretro) and searches again. Your own and picked covers stay. */
    suspend fun redownloadAll() = withContext(Dispatchers.IO) {
        val downloaded = dao.covers().filter { it.source == SOURCE_SGDB || it.source == SOURCE_LIBRETRO }
        downloaded.forEach { c -> c.path.takeIf { it.isNotEmpty() }?.let { File(it).delete() }; dao.deleteCover(c.gameKey) }
        findMissing()
    }

    /** Offline and cheap: picks up sidecar / drop-folder images. Replaces only other manual covers. */
    suspend fun applyManual(roots: List<File>) = withContext(Dispatchers.IO) {
        val covers = dao.covers().associateBy { it.gameKey }
        for (g in dao.allGames()) {
            val file = ManualCovers.find(g.platformId, g.title, g.locator, g.kind == GameKind.APP, roots) ?: continue
            val existing = covers[g.key]
            if (existing != null && (existing.source != SOURCE_MANUAL || existing.updatedAt >= file.lastModified())) continue // picks/removals stand
            store.save(g.key, file.readBytes())?.let { dao.putCover(CoverEntity(g.key, it.absolutePath, SOURCE_MANUAL, file.lastModified())) }
        }
    }

    /**
     * True if Android lets Lighthouse use the network right now. Firewalls such as LineageOS's per-app
     * "Network access" switch make DNS fail for the app only, so check before reporting "0 found".
     */
    fun hasNetwork(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            runCatching { java.net.InetAddress.getByName("www.steamgriddb.com"); true }.getOrDefault(false)
    }

    /**
     * Online: fills in games that have no cover yet. Downloads 3 at a time and writes to the database in
     * batches (each write refreshes HOME, so per-cover writes made the UI stutter).
     */
    suspend fun findMissing(only: Set<String>? = null) = withContext(Dispatchers.IO) {
        if (!hasNetwork()) {
            _job.value = CoverJob("Covers: Lighthouse has no network access", 0, 0, 0, false)
            return@withContext
        }
        val have = dao.covers().map { it.gameKey }.toSet()
        val todo = dao.allGames().filter { it.key !in have && (only == null || it.key in only) }
        val sgdb = settings.steamGridDbKey.takeIf { it.isNotBlank() }?.let(::SteamGridDb)
        val done = java.util.concurrent.atomic.AtomicInteger()
        val found = java.util.concurrent.atomic.AtomicInteger()
        val pending = java.util.Collections.synchronizedList(mutableListOf<CoverEntity>())
        val lastFlush = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        _job.value = CoverJob("Finding covers", 0, todo.size, 0, true)
        kotlinx.coroutines.coroutineScope {
            todo.forEach { g ->
                launch {
                    gate.acquire()
                    try {
                        fetch(g, sgdb)?.let { (bytes, source) ->
                            store.save(g.key, bytes)?.let { pending += CoverEntity(g.key, it.absolutePath, source, System.currentTimeMillis()); found.incrementAndGet() }
                        }
                    } finally {
                        gate.release()
                    }
                    _job.value = CoverJob("Finding covers", done.incrementAndGet(), todo.size, found.get(), true)
                    val now = System.currentTimeMillis()
                    val last = lastFlush.get()
                    if (now - last > 1_500 && lastFlush.compareAndSet(last, now)) flush(pending)
                }
            }
        }
        flush(pending)
        _job.value = CoverJob(
            if (sgdb == null) "Covers (no SteamGridDB key: libretro only)" else "Covers",
            todo.size, todo.size, found.get(), false,
        )
        Log.i(TAG, "findMissing: ${found.get()} of ${todo.size} found")
    }

    // --- Manual choice (cover picker) ---------------------------------------------------------------

    fun steamGridDb(): SteamGridDb? = settings.steamGridDbKey.takeIf { it.isNotBlank() }?.let(::SteamGridDb)

    /** Steam app id for DroidDeck/GameNative shortcuts, else null. */
    fun steamAppIdFor(locator: String): Int? =
        if (locator.endsWith(".steam", true)) runCatching { File(locator).readText().trim().toInt() }.getOrNull() else null

    /** Applies a cover the user picked (download URL or local image bytes). Never overwritten by "find missing". */
    suspend fun applyPicked(gameKey: String, url: String? = null, bytes: ByteArray? = null): Boolean = withContext(Dispatchers.IO) {
        val data = bytes ?: url?.let { httpGet(it) } ?: return@withContext false
        val file = store.save(gameKey, data) ?: return@withContext false
        dao.putCover(CoverEntity(gameKey, file.absolutePath, SOURCE_PICKED, System.currentTimeMillis()))
        true
    }

    /** Back to the plain tile; recorded as "none" so "find missing" doesn't fill it again. */
    suspend fun removeCover(gameKey: String) = withContext(Dispatchers.IO) {
        dao.covers().firstOrNull { it.gameKey == gameKey }?.path?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
        dao.putCover(CoverEntity(gameKey, "", SOURCE_NONE, System.currentTimeMillis()))
    }

    private suspend fun flush(pending: MutableList<CoverEntity>) {
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (batch.isNotEmpty()) dao.putCovers(batch)
    }

    private fun fetch(g: GameEntity, sgdb: SteamGridDb?): Pair<ByteArray, String>? = when {
        g.kind == GameKind.APP -> sgdb?.forTitle(g.title)?.let { it to SOURCE_SGDB }
        g.locator.endsWith(".steam", ignoreCase = true) -> {
            val appId = runCatching { File(g.locator).readText().trim().toInt() }.getOrNull()
            appId?.let { sgdb?.forSteamApp(it) }?.let { it to SOURCE_SGDB }
        }
        else -> sgdb?.forTitle(g.title)?.let { it to SOURCE_SGDB }
            ?: LibretroThumbnails.forRom(g.platformId, g.locator)?.let { it to SOURCE_LIBRETRO }
    }

    companion object {
        private const val TAG = "LighthouseCovers"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_SGDB = "steamgriddb"
        const val SOURCE_LIBRETRO = "libretro"
        /** Box art from the PC, via Moonlight. */
        const val SOURCE_MOONLIGHT = "moonlight"
        /** Chosen in the cover picker (SteamGridDB or a local image). */
        const val SOURCE_PICKED = "picked"
        /** User removed the cover: show the plain tile, don't auto-fill. */
        const val SOURCE_NONE = "none"
    }
}
