package io.cttech.lighthouse.ui.settings

import android.app.Application
import io.cttech.lighthouse.data.db.GameKind
import io.cttech.lighthouse.data.db.SourceEntity
import io.cttech.lighthouse.launch.EmulatorStatus
import io.cttech.lighthouse.launch.GameLauncher
import io.cttech.lighthouse.launch.LaunchProfiles
import io.cttech.lighthouse.library.FolderDetector
import io.cttech.lighthouse.library.LibraryRepository
import io.cttech.lighthouse.library.PlatformCatalog
import io.cttech.lighthouse.library.PlatformDef
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.system.AppSettings
import io.cttech.lighthouse.ui.home.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

data class PlatformRow(val def: PlatformDef, val defaultLabel: String, val count: Int, val hidden: Boolean)
data class FolderRow(val source: SourceEntity, val platformLabel: String, val count: Int)
data class AppRow(val game: Game, val automatic: Boolean)
data class LibraryInfo(
    val platforms: List<PlatformRow> = emptyList(),
    val folders: List<FolderRow> = emptyList(),
    val apps: List<AppRow> = emptyList(),
    val scanning: Boolean = false,
)

/**
 * PC-game apps that keep a folder of one-file-per-game shortcuts in sync themselves (DroidDeck's
 * "Sync all game files to folder", GameNative's Frontend Sync). Lighthouse just watches that folder.
 */
data class SyncApp(val platformId: String, val label: String, val pkg: String, val steps: String, val folder: String?, val count: Int)

/** [extension]: set for a file type with emulators of its own (Switch's .nup), which isn't the platform's choice. */
data class PlatformEmulators(
    val platformId: String,
    val label: String,
    val options: List<EmulatorStatus>,
    val activeId: String?,
    val extension: String? = null,
)

private val SYNC_APPS = listOf(
    SyncApp(
        PlatformCatalog.DROIDDECK, "DroidDeck", "com.droiddeck.launcher",
        steps = "In DroidDeck: Games → open any game → Frontend files → Sync all game files to folder, then choose this folder.",
        folder = null, count = 0,
    ),
    SyncApp(
        PlatformCatalog.GAMENATIVE, "GameNative", "app.gamenative",
        steps = "In GameNative: Settings → Interface → Frontend Sync, then choose this folder for each store you use (Steam, Epic, GOG, Amazon, Custom).",
        folder = null, count = 0,
    ),
)

/** Catalogue platforms in the user's tab order (custom labels applied). */
internal fun orderedPlatforms(settings: AppSettings): List<PlatformDef> {
    val order = settings.tabOrder
    return PlatformCatalog.all
        .sortedBy { def -> order.indexOf(def.id).let { if (it < 0) order.size + PlatformCatalog.order(def.id) else it } }
        .map { def -> settings.tabLabels[def.id]?.let { def.copy(tabLabel = it) } ?: def }
}

/** What the Settings page and the setup wizard show and change about the library: platforms, folders, emulators. */
class LibrarySettingsModel(
    private val app: Application,
    private val repo: LibraryRepository,
    private val settings: AppSettings,
    private val launcher: GameLauncher,
    scope: CoroutineScope,
    shortcutSource: (String) -> String?,
) {
    val info: StateFlow<LibraryInfo> = combine(repo.games, repo.sources, settings.changes) { games, sources, _ ->
        val active = sources.filter { !it.ignored }
        val counts = games.groupingBy { it.platformId }.eachCount()
        val platforms = orderedPlatforms(settings)
            .filter { def -> def.id == PlatformCatalog.ANDROID || (counts[def.id] ?: 0) > 0 || active.any { it.platformId == def.id } }
            .map { def -> PlatformRow(def, PlatformCatalog.byId(def.id)?.tabLabel ?: def.tabLabel, counts[def.id] ?: 0, def.id in settings.hiddenTabs) }
        val folders = sources.sortedWith(compareBy({ it.ignored }, { it.path })).map { src ->
            FolderRow(src, settings.tabLabels[src.platformId] ?: PlatformCatalog.byId(src.platformId)?.tabLabel ?: src.platformId,
                if (src.ignored) 0 else games.count { it.platformId == src.platformId && it.locator.startsWith(src.path + "/") })
        }
        val apps = games.filter { it.kind == GameKind.APP }.sortedBy { it.sortTitle }
            .map { AppRow(it.toUi(settings.gameOverrides, shortcutSource), automatic = it.key !in settings.gameOverrides) }
        LibraryInfo(platforms, folders, apps)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, LibraryInfo())

    fun movePlatform(id: String, delta: Int) {
        val ids = info.value.platforms.map { it.def.id }.toMutableList()
        val i = ids.indexOf(id).takeIf { it >= 0 } ?: return
        val j = (i + delta).coerceIn(0, ids.lastIndex)
        if (i == j) return
        ids.add(j, ids.removeAt(i))
        settings.updateTabOrder(ids)
    }

    fun setPlatformHidden(id: String, hidden: Boolean) = settings.setTabHidden(id, hidden)
    fun renamePlatform(id: String, label: String) = settings.updateTabLabel(id, label.takeIf { it.isNotBlank() && it != PlatformCatalog.byId(id)?.tabLabel })

    fun syncApps(info: LibraryInfo): List<SyncApp> {
        val pm = app.packageManager
        return SYNC_APPS.filter { pm.getLaunchIntentForPackage(it.pkg) != null }.map { sync ->
            val folders = info.folders.filter { it.source.platformId == sync.platformId && !it.source.ignored }
            sync.copy(folder = folders.firstOrNull()?.source?.path, count = folders.sumOf { it.count })
        }
    }

    /**
     * The folder to point [sync] at: its existing Lighthouse folder, or a new one next to the other platform
     * folders (e.g. SD/Games/DroidDeck), created and added so it's ready in the app's folder picker.
     */
    fun prepareSyncFolder(sync: SyncApp): String? {
        sync.folder?.let { return it }
        val existing = info.value.folders.map { File(it.source.path) }
            .firstOrNull { it.parentFile?.name?.trim()?.lowercase() == "games" }?.parentFile
        val base = existing ?: repo.storageRoots().firstOrNull()?.let { File(it, "Games") } ?: return null
        val dir = File(base, sync.label)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        repo.addFolder(dir.absolutePath, sync.platformId)
        return dir.absolutePath
    }

    fun addFolder(path: String, platformId: String) = repo.addFolder(path, platformId)
    fun removeFolder(source: SourceEntity) = repo.removeFolder(source)
    fun setFolderPlatform(source: SourceEntity, platformId: String) = repo.setFolderPlatform(source, platformId)
    fun guessPlatform(path: String): String? = FolderDetector.guessPlatform(File(path))
    fun storageRoots(): List<File> = repo.storageRoots()

    /**
     * Emulator status for every platform with games or folders that starts through a launch profile. Android apps,
     * Moonlight and Shortcuts start themselves, so there's no emulator to show. A file type with an emulator of its own
     * (Switch's .nup, Peanuts Native) follows its platform once that emulator is installed or there are such games.
     * Checks cores with root, so it suspends.
     */
    suspend fun emulatorStatus(): List<PlatformEmulators> = withContext(Dispatchers.IO) {
        val games = repo.games.first()
        info.value.platforms.flatMap { row ->
            val id = row.def.id
            val platform = launcher.status(id).takeIf { it.isNotEmpty() }
                ?.let { PlatformEmulators(id, row.def.tabLabel, it, launcher.activeProfile(id)?.first?.id) }
            val ownTypes = LaunchProfiles.forPlatform(app, id).flatMap { it.extensions }.distinct().mapNotNull { ext ->
                val options = launcher.status(id, ext)
                val wanted = options.any { it.pkg != null } || games.any { it.platformId == id && it.locator.endsWith(".$ext", ignoreCase = true) }
                if (!wanted) null
                else PlatformEmulators(id, "${row.def.tabLabel} · .$ext games", options, launcher.activeProfile(id, extension = ext)?.first?.id, ext)
            }
            listOfNotNull(platform) + ownTypes
        }
    }

    fun chooseEmulator(platformId: String, profileId: String) = settings.updateEmulator(platformId, profileId)
    suspend fun grantStorage(pkg: String): Boolean = launcher.grantStorageWithRoot(pkg)
}
