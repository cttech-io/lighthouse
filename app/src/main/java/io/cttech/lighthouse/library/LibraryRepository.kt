package io.cttech.lighthouse.library

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import io.cttech.lighthouse.data.db.FavouriteEntity
import io.cttech.lighthouse.data.db.GameEntity
import io.cttech.lighthouse.data.db.GameKind
import io.cttech.lighthouse.data.db.LibraryDao
import io.cttech.lighthouse.data.db.PlayEntity
import io.cttech.lighthouse.data.db.SourceEntity
import io.cttech.lighthouse.storage.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Owns the library: detects folders, scans ROMs and apps, watches for changes, stores favourites. */
class LibraryRepository(
    private val app: Application,
    private val dao: LibraryDao,
    private val storage: StorageAccess,
    private val afterScan: suspend (roots: List<File>) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val pendingRescans = ConcurrentHashMap<String, Job>()
    private val watcher = storage.createWatcher { platformId -> scheduleRescan(platformId) }

    val games: Flow<List<GameEntity>> = dao.observeGames().onEach { list -> lastGames = list.associateBy { it.key } }
    /** gameKey -> cover file path. */
    val coverPaths: Flow<Map<String, String>> =
        dao.observeCovers().map { list -> list.filter { it.path.isNotEmpty() }.associate { it.gameKey to it.path } } // "none" = removed
    val favourites: Flow<List<String>> = dao.observeFavourites().map { list -> list.map { it.gameKey } }
    val plays: Flow<List<PlayEntity>> = dao.observePlays()
    val hidden: Flow<List<String>> = dao.observeHidden()
    /** Every known ROM folder, including ones the user switched off ([SourceEntity.ignored]). */
    val sources: Flow<List<SourceEntity>> = dao.observeSources()

    /** Adds a folder by hand (Settings → Library or setup). Re-adding a removed folder switches it back on. */
    fun addFolder(path: String, platformId: String) = editSources {
        val existing = dao.sourceByPath(path)
        if (existing != null) dao.updateSource(existing.copy(platformId = platformId, ignored = false))
        else dao.insertSources(listOf(SourceEntity(platformId = platformId, path = path, autoDetected = false)))
    }

    /** Auto-detected folders are only switched off (so detection doesn't bring them back); added ones are deleted. */
    fun removeFolder(source: SourceEntity) = editSources {
        if (source.autoDetected) dao.updateSource(source.copy(ignored = true)) else dao.deleteSource(source.id)
    }

    fun setFolderPlatform(source: SourceEntity, platformId: String) = editSources {
        dao.updateSource(source.copy(platformId = platformId, ignored = false))
    }

    private fun editSources(edit: suspend () -> Unit) {
        scope.launch {
            lock.withLock {
                edit()
                if (storage.hasAccess(app)) scanAllRomsLocked()
            }
        }
    }

    @Volatile private var lastGames: Map<String, GameEntity> = emptyMap()

    /** Latest stored entity for [key] (from the last emission the UI saw). */
    fun gameEntity(key: String): GameEntity? = lastGames[key]

    fun setHidden(key: String, hidden: Boolean) {
        scope.launch { if (hidden) dao.hide(io.cttech.lighthouse.data.db.HiddenEntity(key, System.currentTimeMillis())) else dao.unhide(key) }
    }

    private val _scanInfo = MutableStateFlow(ScanInfo())
    /** Free space per volume and when the library was last refreshed, for Home's status strip. */
    val scanInfo: StateFlow<ScanInfo> = _scanInfo

    fun hasStorageAccess(): Boolean = storage.hasAccess(app)
    fun storageAccessIntent(): Intent? = storage.accessIntent(app)

    /** Full refresh: apps, then (if allowed) folder detection and every ROM platform. Safe to call on every resume. */
    fun refreshAll() {
        scope.launch {
            lock.withLock {
                val started = System.currentTimeMillis()
                refreshAppsLocked()
                if (storage.hasAccess(app)) {
                    detectSourcesLocked()
                    scanAllRomsLocked()
                }
                _scanInfo.value = ScanInfo(volumes = volumeSpace(), lastScanAt = System.currentTimeMillis())
                runCatching { afterScan(storageRoots()) }.onFailure { Log.w(TAG, "afterScan failed", it) }
                Log.i(TAG, "refreshAll in ${System.currentTimeMillis() - started} ms")
            }
        }
    }

    fun refreshApps() {
        scope.launch { lock.withLock { refreshAppsLocked() } }
    }

    fun recordLaunch(key: String) {
        scope.launch { dao.recordLaunch(key, System.currentTimeMillis()) }
    }

    fun toggleFavourite(key: String) {
        scope.launch {
            if (dao.isFavourite(key) > 0) dao.removeFavourite(key)
            else dao.addFavourite(FavouriteEntity(key, System.currentTimeMillis()))
        }
    }

    // --- internals (call with [lock] held) -------------------------------------------------------

    private suspend fun refreshAppsLocked() {
        val pm = app.packageManager
        val now = System.currentTimeMillis()
        val apps = launcherActivities(pm)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName.isUserFacingApp(it) }
            .map { info ->
                val title = pm.getApplicationLabel(info).toString()
                val pkg = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
                GameEntity(
                    platformId = PlatformCatalog.ANDROID,
                    kind = GameKind.APP,
                    locator = info.packageName,
                    title = title,
                    sortTitle = TitleCleaner.sortKey(title),
                    sizeBytes = 0,
                    modifiedAt = pkg?.lastUpdateTime ?: 0,
                    addedAt = pkg?.firstInstallTime ?: now,
                    isGame = AppClassifier.isGameLike(info),
                )
            }
        dao.replaceGames(PlatformCatalog.ANDROID, GameKind.APP, apps)
    }

    /** User-installed (or updated system) apps, minus Lighthouse's own builds. */
    private fun String.isUserFacingApp(info: ApplicationInfo): Boolean {
        if (startsWith("io.cttech.lighthouse")) return false
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return !system || updatedSystem
    }

    private suspend fun detectSourcesLocked() {
        val found = FolderDetector.detect(storageRoots())
        dao.insertSources(found.map { SourceEntity(platformId = it.platformId, path = it.dir.absolutePath, autoDetected = true) })
        // Earlier versions auto-detected app install folders (GameNative's own game folder) as shortcut folders.
        dao.sources().filter { it.autoDetected && FolderDetector.isAppInstallDir(File(it.path)) }.forEach { dao.deleteSource(it.id) }
    }

    private suspend fun scanAllRomsLocked() {
        val byPlatform = activeSources().groupBy { it.platformId }
        // Every ROM platform, even without folders: a platform whose last folder was removed must empty out.
        for (def in PlatformCatalog.all) {
            if (def.id != PlatformCatalog.ANDROID) scanPlatformLocked(def.id, byPlatform[def.id].orEmpty())
        }
        watcher.watch(byPlatform.mapValues { (_, s) -> s.map { File(it.path) } })
    }

    private suspend fun activeSources() = dao.sources().filter { !it.ignored }

    private suspend fun scanPlatformLocked(platformId: String, sources: List<SourceEntity>) {
        val def = PlatformCatalog.byId(platformId) ?: return
        val roms = RomScanner.scan(def, sources.map { File(it.path) }.filter { it.isDirectory })
        dao.replaceGames(
            platformId, GameKind.ROM,
            roms.map {
                GameEntity(
                    platformId = platformId, kind = GameKind.ROM, locator = it.path, title = it.title,
                    sortTitle = it.sortKey, sizeBytes = it.sizeBytes, modifiedAt = it.modifiedAt, addedAt = it.modifiedAt,
                )
            },
        )
    }

    /** File events arrive in bursts (a copy fires many); wait for quiet, then rescan that platform only. */
    private fun scheduleRescan(platformId: String) {
        pendingRescans[platformId]?.cancel()
        pendingRescans[platformId] = scope.launch {
            delay(1_500)
            lock.withLock {
                val sources = activeSources()
                scanPlatformLocked(platformId, sources.filter { it.platformId == platformId })
                watcher.watch(sources.groupBy { it.platformId }.mapValues { (_, s) -> s.map { File(it.path) } })
                Log.i(TAG, "rescanned $platformId after file change")
            }
        }
    }

    private fun volumeSpace(): List<VolumeSpace> =
        app.getSystemService(StorageManager::class.java).storageVolumes
            .filter { it.state == android.os.Environment.MEDIA_MOUNTED }
            .mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                VolumeSpace(if (v.isPrimary) "Internal" else if (v.isRemovable) "SD" else v.getDescription(app), dir.freeSpace)
            }

    /** Mounted storage volumes (internal, SD…); also where user themes and covers are looked up. */
    fun storageRoots(): List<File> =
        app.getSystemService(StorageManager::class.java).storageVolumes
            .filter { it.state == android.os.Environment.MEDIA_MOUNTED }
            .mapNotNull { it.directory }

    private fun launcherActivities(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
    }

    private companion object { const val TAG = "LighthouseLibrary" }
}

data class VolumeSpace(val label: String, val freeBytes: Long)
data class ScanInfo(val volumes: List<VolumeSpace> = emptyList(), val lastScanAt: Long = 0)
