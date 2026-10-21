package io.cttech.lighthouse.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.cttech.lighthouse.LighthouseApp
import io.cttech.lighthouse.data.db.GameEntity
import io.cttech.lighthouse.data.db.GameKind
import io.cttech.lighthouse.launch.GameLauncher
import io.cttech.lighthouse.launch.LaunchResult
import io.cttech.lighthouse.library.PlatformCatalog
import kotlinx.coroutines.launch
import io.cttech.lighthouse.library.ScanInfo
import io.cttech.lighthouse.model.HomeItem
import io.cttech.lighthouse.model.HomeRow
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.HomeLayout
import io.cttech.lighthouse.model.HomeTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

data class HomeState(
    val tabs: List<HomeTab> = listOf(HomeTab.Home),
    val homeRows: List<HomeRow> = emptyList(),
    /** Hidden items, listed in Settings so they can be unhidden. */
    val hidden: List<Game> = emptyList(),
    /** e.g. "31 games · 14 apps · SD 87 GB free · Internal 52 GB free · scanned 14:05" */
    val statusLine: String = "",
    val selectedTab: Int = 0,
    val favourites: Set<String> = emptySet(),
    /** Last focused game per tab, so switching tabs (or a rescan) returns you to the same game. */
    val focusByTab: Map<String, FocusMemory> = emptyMap(),
    val gamesByTab: Map<String, List<Game>> = emptyMap(),
    /** +1 / -1: direction of the last tab move, for the slide animation (survives wrap-around). */
    val tabDirection: Int = 1,
    /** Show the "grant All files access" prompt (sideload flavour, not yet granted, not dismissed). */
    val showAccessPrompt: Boolean = false,
    val loaded: Boolean = false,
) {
    val currentTab: HomeTab get() = tabs[selectedTab.coerceIn(0, tabs.lastIndex)]

    fun gamesFor(tab: HomeTab): List<Game> = gamesByTab[tab.key].orEmpty()

    /** Index of the remembered game; if it has gone (deleted), its old position, clamped. */
    fun focusIndexFor(tab: HomeTab): Int {
        val games = gamesFor(tab)
        val mem = focusByTab[tab.key] ?: return 0
        val byKey = games.indexOfFirst { it.key == mem.gameKey }
        return if (byKey >= 0) byKey else mem.index.coerceIn(0, (games.size - 1).coerceAtLeast(0))
    }
}

/** Remembered by game, not position, so games sorting in ahead of you don't move your selection. */
data class FocusMemory(val gameKey: String, val index: Int)

private data class UiPrefs(
    /** null until the user picks a tab: then we start on Favs if it has anything, else the first tab with games. */
    val selectedKey: String? = null,
    val focusByTab: Map<String, FocusMemory> = emptyMap(),
    val tabDirection: Int = 1,
    val hasAccess: Boolean = true,
    val accessDismissed: Boolean = false,
)

private val SYNC_APPS = listOf(
    HomeViewModel.SyncApp(
        PlatformCatalog.DROIDDECK, "DroidDeck", "com.droiddeck.launcher",
        steps = "In DroidDeck: Games → open any game → Frontend files → Sync all game files to folder, then choose this folder.",
        folder = null, count = 0,
    ),
    HomeViewModel.SyncApp(
        PlatformCatalog.GAMENATIVE, "GameNative", "app.gamenative",
        steps = "In GameNative: Settings → Interface → Frontend Sync, then choose this folder for each store you use (Steam, Epic, GOG, Amazon, Custom).",
        folder = null, count = 0,
    ),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as LighthouseApp).repository
    private val settings = (app as LighthouseApp).settings
    private val launcher = GameLauncher(app) { platformId -> settings.emulatorChoice[platformId] }

    private val _launchIssue = MutableStateFlow<LaunchResult?>(null)
    /** A launch that didn't start (missing emulator, permission, core…), shown as a dialog with a fix. */
    val launchIssue: StateFlow<LaunchResult?> = _launchIssue

    fun launch(game: Game) {
        viewModelScope.launch {
            when (val r = launcher.launch(game)) {
                LaunchResult.Started -> repo.recordLaunch(game.key)
                else -> { _launchIssue.value = r; pendingRetry = game }
            }
        }
    }

    private var pendingRetry: Game? = null

    fun dismissLaunchIssue() { _launchIssue.value = null; pendingRetry = null }

    /** Root extra from the dialog: grant the emulator storage access, then retry the same game. */
    fun grantStorageAndRetry(pkg: String) {
        viewModelScope.launch {
            val game = pendingRetry
            if (launcher.grantStorageWithRoot(pkg) && game != null) { _launchIssue.value = null; launch(game) }
            else _launchIssue.value = LaunchResult.Failed("Couldn't grant storage access with root. Use the app's settings instead.")
        }
    }

    fun openAppInfo(pkg: String) = launcher.openAppInfo(pkg)

    // --- Covers (Start menu) ----------------------------------------------------------------------
    private val coverService = (app as LighthouseApp).covers
    val coverJob = coverService.job
    /** Facts shown on the Settings page. */
    data class SettingsInfo(val network: Boolean = true, val storageAccess: Boolean = true, val rootAvailable: Boolean = false)
    private val _settingsInfo = MutableStateFlow(SettingsInfo())
    val settingsInfo: StateFlow<SettingsInfo> = _settingsInfo

    fun refreshSettingsInfo() {
        viewModelScope.launch {
            _settingsInfo.value = SettingsInfo(
                network = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { coverService.hasNetwork() },
                storageAccess = repo.hasStorageAccess(),
                rootAvailable = io.cttech.lighthouse.system.Root.available(),
            )
        }
    }

    /** Checks a SteamGridDB key with one search call. */
    suspend fun testSteamGridDbKey(key: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        io.cttech.lighthouse.covers.SteamGridDb(key).isValid()
    }

    // --- Game options + cover picker ----------------------------------------------------------------
    private val _optionsFor = MutableStateFlow<Game?>(null)
    /** Game whose options sheet is open (Select / long-press). */
    val optionsFor: StateFlow<Game?> = _optionsFor
    private val _pickerFor = MutableStateFlow<Game?>(null)
    /** Game whose cover picker is open. */
    val pickerFor: StateFlow<Game?> = _pickerFor

    fun openOptions(game: Game) { _optionsFor.value = game }
    fun closeOptions() { _optionsFor.value = null }
    fun openCoverPicker(game: Game) { _optionsFor.value = null; _pickerFor.value = game }
    fun closeCoverPicker() { _pickerFor.value = null }

    fun hasSteamGridDbKey(): Boolean = coverService.steamGridDb() != null
    fun steamAppIdFor(game: Game): Int? = game.path?.let(coverService::steamAppIdFor)

    suspend fun searchCovers(term: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        coverService.steamGridDb()?.search(term).orEmpty()
    }

    suspend fun coverGrids(gameId: Int? = null, steamAppId: Int? = null) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        coverService.steamGridDb()?.grids(gameId, steamAppId).orEmpty()
    }

    suspend fun applyCover(game: Game, url: String): Boolean = coverService.applyPicked(game.key, url = url).also { if (it) closeCoverPicker() }

    suspend fun applyCoverImage(game: Game, uri: android.net.Uri): Boolean {
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return false
        return coverService.applyPicked(game.key, bytes = bytes).also { if (it) closeCoverPicker() }
    }

    fun removeCover(game: Game) { viewModelScope.launch { coverService.removeCover(game.key) }; _optionsFor.value = null; _pickerFor.value = null }

    // --- Themes ------------------------------------------------------------------------------------
    private val themeRepo = io.cttech.lighthouse.theme.ThemeRepository(app)
    private val _themes = MutableStateFlow<List<io.cttech.lighthouse.theme.LhTheme>>(emptyList())
    /** Bundled + user themes (Lighthouse/themes/<id>/theme.json), reloaded on every resume. */
    val themes: StateFlow<List<io.cttech.lighthouse.theme.LhTheme>> = _themes

    fun reloadThemes() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _themes.value = themeRepo.loadAll(repo.storageRoots()) }
    }

    fun findMissingCovers() { viewModelScope.launch { coverService.findMissing() } }
    fun rescanLibrary() = repo.refreshAll(force = true)

    fun openApp(pkg: String) {
        getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { getApplication<Application>().startActivity(it) }
    }
    private val prefs = MutableStateFlow(UiPrefs(hasAccess = repo.hasStorageAccess()))

    private data class LibrarySnapshot(val games: List<GameEntity>, val covers: Map<String, String>, val hidden: Set<String>)

    /** Games with covers and hidden keys; re-emits when tab/emulator/"counts as game" settings change. */
    private val gamesAndCovers = combine(repo.games, repo.coverPaths, repo.hidden, settings.changes) { g, c, h, _ -> LibrarySnapshot(g, c, h.toSet()) }

    val state: StateFlow<HomeState> = combine(gamesAndCovers, repo.favourites, repo.plays, repo.scanInfo, prefs) { (allGames, covers, hiddenKeys), favKeys, plays, scan, p ->
        val hiddenTabs = settings.hiddenTabs
        // Hidden items, and everything on a hidden platform, stay out of the shelves (the app drawer still has apps).
        val games = allGames.filter { it.key !in hiddenKeys && it.platformId !in hiddenTabs }
        val hiddenUi = allGames.filter { it.key in hiddenKeys }.map { it.toUi(settings.gameOverrides) }
        val ui = games.map { it.toUi(settings.gameOverrides).copy(coverPath = covers[it.key]) }
        val byPlatform = ui.groupBy { it.platformId }
        val byKey = ui.associateBy { it.key }
        val platformTabs = orderedPlatforms()
            .filter { it.id !in hiddenTabs && (it.id == PlatformCatalog.ANDROID || byPlatform[it.id].orEmpty().isNotEmpty()) }
            .map { HomeTab.PlatformTab(it) }
        val tabs = listOf(HomeTab.Home) + platformTabs
        val gamesByTab = buildMap {
            platformTabs.forEach { put(it.key, byPlatform[it.platform.id].orEmpty()) }
        }
        val homeRows = buildHomeRows(
            tabs = tabs,
            byKey = byKey,
            byPlatform = byPlatform,
            favKeys = favKeys,
            // Only things you play: ROMs and game-like apps (not Bitwarden & co.).
            recentlyPlayed = plays.map { it.gameKey }.filter { key -> byKey[key]?.isGame == true },
            recentlyAdded = games.filter { System.currentTimeMillis() - it.addedAt < RECENT_WINDOW_MS }
                .sortedByDescending { it.addedAt }.map { it.key },
            order = settings.homeSectionOrder,
            hidden = settings.hiddenHomeSections,
        )
        HomeState(
            tabs = tabs,
            homeRows = homeRows,
            hidden = hiddenUi,
            statusLine = statusLine(games, scan),
            // Home always has something (at least the Platforms row), so it is the natural starting tab.
            selectedTab = tabs.indexOfFirst { it.key == (p.selectedKey ?: HomeTab.Home.key) }.coerceAtLeast(0),
            favourites = favKeys.toSet(),
            focusByTab = p.focusByTab,
            gamesByTab = gamesByTab,
            tabDirection = p.tabDirection,
            showAccessPrompt = !p.hasAccess && !p.accessDismissed && repo.storageAccessIntent() != null,
            loaded = true,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // build state off the main thread
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** Called from Activity.onResume: re-check permission (it may have just been granted) and rescan. */
    fun onResume() {
        reloadThemes()
        prefs.update { it.copy(hasAccess = repo.hasStorageAccess()) }
        repo.refreshAll()
    }

    fun storageAccessIntent(): Intent? = repo.storageAccessIntent()

    fun dismissAccessPrompt() = prefs.update { it.copy(accessDismissed = true) }

    private val _homePresses = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when the Home button is pressed while Lighthouse is already the foreground home app. */
    val homePresses: kotlinx.coroutines.flow.SharedFlow<Unit> = _homePresses

    /** Home button: back to the HOME tab, top-left item (or where you were, per Settings → Controls). */
    fun onHomePressed() {
        val keepPlace = settings.homeButton == io.cttech.lighthouse.system.HomeButtonAction.LastPosition
        prefs.update {
            it.copy(selectedKey = HomeTab.Home.key, tabDirection = -1, focusByTab = if (keepPlace) it.focusByTab else it.focusByTab - HomeTab.Home.key)
        }
        _homePresses.tryEmit(Unit)
    }

    /** Moves one tab left/right, wrapping around like a console shelf. */
    fun moveTab(delta: Int) {
        val tabs = state.value.tabs
        val next = tabs[(state.value.selectedTab + delta).mod(tabs.size)]
        // (selectedKey may still be null: the move is relative to whatever tab is currently shown)
        prefs.update { it.copy(selectedKey = next.key, tabDirection = delta.coerceIn(-1, 1)) }
    }

    fun selectTab(index: Int) {
        val tabs = state.value.tabs
        val target = tabs.getOrNull(index) ?: return
        val dir = if (index >= state.value.selectedTab) 1 else -1
        prefs.update { it.copy(selectedKey = target.key, tabDirection = dir) }
    }

    fun toggleLayout() = settings.updateLayout(if (settings.layout == HomeLayout.Carousel) HomeLayout.Grid else HomeLayout.Carousel)

    fun onFocused(tab: HomeTab, index: Int) {
        val game = state.value.gamesFor(tab).getOrNull(index) ?: return
        val mem = FocusMemory(game.key, index)
        prefs.update { if (it.focusByTab[tab.key] == mem) it else it.copy(focusByTab = it.focusByTab + (tab.key to mem)) }
    }

    fun toggleFavourite(game: Game) = repo.toggleFavourite(game.key)

    fun hide(game: Game) { repo.setHidden(game.key, true); _optionsFor.value = null }

    /** Unpins a shortcut another app added (Shortcuts tab); Android forgets it, so it's gone, not hidden. */
    fun removeShortcut(game: Game) {
        val (pkg, id) = game.key.substringAfter('|').split('/', limit = 2).takeIf { it.size == 2 } ?: return
        io.cttech.lighthouse.library.PinnedShortcuts.remove(getApplication(), pkg, id)
        _optionsFor.value = null
        repo.refreshAll(force = true)
    }
    fun unhide(game: Game) = repo.setHidden(game.key, false)

    fun recordLaunch(game: Game) = repo.recordLaunch(game.key)

    /** Flips whether [game] counts as a game; back to automatic when it matches the guess. */
    fun toggleCountsAsGame(game: Game) {
        val entity = repo.gameEntity(game.key)
        val next = !game.isGame
        settings.updateGameOverride(game.key, if (entity != null && entity.isGame == next) null else next)
        _optionsFor.value = _optionsFor.value?.takeIf { it.key == game.key }?.copy(isGame = next)
    }

    // --- Library / platforms / emulators (Settings + setup) -------------------------------------------

    /** Catalogue platforms in the user's tab order (custom labels applied). */
    private fun orderedPlatforms(): List<io.cttech.lighthouse.library.PlatformDef> {
        val order = settings.tabOrder
        return PlatformCatalog.all
            .sortedBy { def -> order.indexOf(def.id).let { if (it < 0) order.size + PlatformCatalog.order(def.id) else it } }
            .map { def -> settings.tabLabels[def.id]?.let { def.copy(tabLabel = it) } ?: def }
    }

    data class PlatformRow(val def: io.cttech.lighthouse.library.PlatformDef, val defaultLabel: String, val count: Int, val hidden: Boolean)
    data class FolderRow(val source: io.cttech.lighthouse.data.db.SourceEntity, val platformLabel: String, val count: Int)
    data class AppRow(val game: Game, val automatic: Boolean)
    data class LibraryInfo(
        val platforms: List<PlatformRow> = emptyList(),
        val folders: List<FolderRow> = emptyList(),
        val apps: List<AppRow> = emptyList(),
        val scanning: Boolean = false,
    )

    val libraryInfo: StateFlow<LibraryInfo> = combine(repo.games, repo.sources, settings.changes) { games, sources, _ ->
        val active = sources.filter { !it.ignored }
        val counts = games.groupingBy { it.platformId }.eachCount()
        val platforms = orderedPlatforms()
            .filter { def -> def.id == PlatformCatalog.ANDROID || (counts[def.id] ?: 0) > 0 || active.any { it.platformId == def.id } }
            .map { def -> PlatformRow(def, PlatformCatalog.byId(def.id)?.tabLabel ?: def.tabLabel, counts[def.id] ?: 0, def.id in settings.hiddenTabs) }
        val folders = sources.sortedWith(compareBy({ it.ignored }, { it.path })).map { src ->
            FolderRow(src, settings.tabLabels[src.platformId] ?: PlatformCatalog.byId(src.platformId)?.tabLabel ?: src.platformId,
                if (src.ignored) 0 else games.count { it.platformId == src.platformId && it.locator.startsWith(src.path + "/") })
        }
        val apps = games.filter { it.kind == GameKind.APP }.sortedBy { it.sortTitle }
            .map { AppRow(it.toUi(settings.gameOverrides), automatic = it.key !in settings.gameOverrides) }
        LibraryInfo(platforms, folders, apps)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryInfo())

    fun movePlatform(id: String, delta: Int) {
        val ids = libraryInfo.value.platforms.map { it.def.id }.toMutableList()
        val i = ids.indexOf(id).takeIf { it >= 0 } ?: return
        val j = (i + delta).coerceIn(0, ids.lastIndex)
        if (i == j) return
        ids.add(j, ids.removeAt(i))
        settings.updateTabOrder(ids)
    }

    fun setPlatformHidden(id: String, hidden: Boolean) = settings.setTabHidden(id, hidden)
    fun renamePlatform(id: String, label: String) = settings.updateTabLabel(id, label.takeIf { it.isNotBlank() && it != PlatformCatalog.byId(id)?.tabLabel })

    /**
     * PC-game apps that keep a folder of one-file-per-game shortcuts in sync themselves (DroidDeck's
     * "Sync all game files to folder", GameNative's Frontend Sync). Lighthouse just watches that folder.
     */
    data class SyncApp(val platformId: String, val label: String, val pkg: String, val steps: String, val folder: String?, val count: Int)

    fun syncApps(info: LibraryInfo): List<SyncApp> {
        val pm = getApplication<Application>().packageManager
        return SYNC_APPS.filter { pm.getLaunchIntentForPackage(it.pkg) != null }.map { app ->
            val folders = info.folders.filter { it.source.platformId == app.platformId && !it.source.ignored }
            app.copy(folder = folders.firstOrNull()?.source?.path, count = folders.sumOf { it.count })
        }
    }

    /**
     * The folder to point [app] at: its existing Lighthouse folder, or a new one next to the other platform
     * folders (e.g. SD/Games/DroidDeck), created and added so it's ready in the app's folder picker.
     */
    fun prepareSyncFolder(app: SyncApp): String? {
        app.folder?.let { return it }
        val existing = libraryInfo.value.folders.map { File(it.source.path) }
            .firstOrNull { it.parentFile?.name?.trim()?.lowercase() == "games" }?.parentFile
        val base = existing ?: repo.storageRoots().firstOrNull()?.let { File(it, "Games") } ?: return null
        val dir = File(base, app.label)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        repo.addFolder(dir.absolutePath, app.platformId)
        return dir.absolutePath
    }

    fun addFolder(path: String, platformId: String) = repo.addFolder(path, platformId)
    fun removeFolder(source: io.cttech.lighthouse.data.db.SourceEntity) = repo.removeFolder(source)
    fun setFolderPlatform(source: io.cttech.lighthouse.data.db.SourceEntity, platformId: String) = repo.setFolderPlatform(source, platformId)
    fun guessPlatform(path: String): String? = io.cttech.lighthouse.library.FolderDetector.guessPlatform(File(path))
    fun storageRoots(): List<File> = repo.storageRoots()

    data class PlatformEmulators(val platformId: String, val label: String, val options: List<io.cttech.lighthouse.launch.EmulatorStatus>, val activeId: String?)

    /** Emulator status for every ROM platform that has games or folders. Checks cores with root, so it suspends. */
    suspend fun emulatorStatus(): List<PlatformEmulators> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        libraryInfo.value.platforms.filter { it.def.id != PlatformCatalog.ANDROID }.map { row ->
            PlatformEmulators(row.def.id, row.def.tabLabel, launcher.status(row.def.id), launcher.activeProfile(row.def.id)?.first?.id)
        }
    }

    fun chooseEmulator(platformId: String, profileId: String) = settings.updateEmulator(platformId, profileId)
    suspend fun grantStorage(pkg: String): Boolean = launcher.grantStorageWithRoot(pkg)

    // --- Cover offer ---------------------------------------------------------------------------------
    val coverOffer = coverService.offer
    fun answerCoverOffer(fetch: Boolean, always: Boolean = false) {
        if (always) settings.updateCoverAuto(io.cttech.lighthouse.system.CoverAutoMode.Always)
        viewModelScope.launch { coverService.answerOffer(fetch) }
    }
    fun redownloadAllCovers() { viewModelScope.launch { coverService.redownloadAll() } }

    /** Home remembers the focused item by key, across all its rows. */
    fun onHomeFocused(itemKey: String) = prefs.update {
        val mem = FocusMemory(itemKey, 0)
        if (it.focusByTab[HomeTab.Home.key] == mem) it else it.copy(focusByTab = it.focusByTab + (HomeTab.Home.key to mem))
    }
}

private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Game-streaming clients grouped into the Streaming row (Sunshine host apps join this in v0.3).
 * Not PC emulators: DroidDeck, GameNative and GameHub run games locally and have their own tabs.
 */
private val STREAMING_PACKAGES = listOf("com.limelight", "com.valvesoftware.steamlink")

private fun buildHomeRows(
    tabs: List<HomeTab>,
    byKey: Map<String, Game>,
    byPlatform: Map<String, List<Game>>,
    favKeys: List<String>,
    recentlyPlayed: List<String>,
    recentlyAdded: List<String>,
    order: List<String>,
    hidden: Set<String>,
): List<HomeRow> {
    fun games(keys: List<String>, limit: Int) = keys.mapNotNull { byKey[it] }.take(limit).map { HomeItem.GameItem(it) }
    val streaming = STREAMING_PACKAGES.mapNotNull { pkg -> byKey["${PlatformCatalog.ANDROID}|$pkg"] }.map { HomeItem.GameItem(it) }
    val platforms = tabs.mapIndexedNotNull { i, t ->
        (t as? HomeTab.PlatformTab)?.let { HomeItem.PlatformItem(i, it.platform, byPlatform[it.platform.id].orEmpty().size) }
    }
    val rows = mapOf(
        "favourites" to HomeRow("favourites", HOME_SECTION_TITLES.getValue("favourites"), games(favKeys, 30)),
        "continue" to HomeRow("continue", HOME_SECTION_TITLES.getValue("continue"), games(recentlyPlayed, 10)),
        "recent" to HomeRow("recent", HOME_SECTION_TITLES.getValue("recent"), games(recentlyAdded, 15)),
        "streaming" to HomeRow("streaming", HOME_SECTION_TITLES.getValue("streaming"), streaming),
        "platforms" to HomeRow("platforms", HOME_SECTION_TITLES.getValue("platforms"), platforms),
        "moonlight" to HomeRow(
            "moonlight", HOME_SECTION_TITLES.getValue("moonlight"),
            byPlatform[PlatformCatalog.MOONLIGHT].orEmpty().sortedBy { it.title.lowercase() }.take(30).map { HomeItem.GameItem(it) },
        ),
    )
    // The user's order (Settings → Appearance → Home sections); the first one shown gets the large covers.
    return order.filter { it !in hidden }.mapNotNull { rows[it] }.filter { it.items.isNotEmpty() }
        .mapIndexed { i, row -> if (i == 0 && row.id != "platforms") row.copy(hero = true) else row }
}

val HOME_SECTION_TITLES = mapOf(
    "favourites" to "Favourites",
    "continue" to "Continue playing",
    "recent" to "Recently added",
    "streaming" to "Streaming",
    "moonlight" to "On your PC",
    "platforms" to "Platforms",
)

private fun statusLine(games: List<GameEntity>, scan: ScanInfo): String {
    val roms = games.count { it.kind == GameKind.ROM }
    val apps = games.count { it.kind == GameKind.APP }
    val parts = mutableListOf("$roms ${if (roms == 1) "game" else "games"}", "$apps apps")
    scan.volumes.forEach { parts += "${it.label} ${formatSize(it.freeBytes)} free" }
    if (scan.lastScanAt > 0) {
        parts += "scanned " + java.time.Instant.ofEpochMilli(scan.lastScanAt).atZone(java.time.ZoneId.systemDefault())
            .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }
    return parts.joinToString("  ·  ")
}

private fun GameEntity.toUi(overrides: Map<String, Boolean> = emptyMap()): Game = when (kind) {
    GameKind.APP -> Game(key = key, platformId = platformId, title = title, subtitle = "Android app", packageName = locator, isGame = overrides[key] ?: isGame)
    GameKind.ROM -> Game(
        key = key,
        platformId = platformId,
        title = title,
        subtitle = "${File(locator).extension.uppercase(Locale.ROOT)} · ${formatSize(sizeBytes)}",
        path = locator,
    )
    GameKind.STREAM -> Game(
        key = key,
        platformId = platformId,
        title = title,
        subtitle = io.cttech.lighthouse.library.MoonlightSource.app(locator)?.hostName?.takeIf { it.isNotEmpty() }?.let { "On $it" } ?: "Moonlight",
    )
    GameKind.SHORTCUT -> Game(key = key, platformId = platformId, title = title, subtitle = "Shortcut", isGame = overrides[key] ?: isGame)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.UK, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.UK, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.UK, "%.0f KB", bytes / 1024.0)
}
