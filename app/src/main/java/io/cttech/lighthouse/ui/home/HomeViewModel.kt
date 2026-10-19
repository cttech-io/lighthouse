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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

data class HomeState(
    val tabs: List<HomeTab> = listOf(HomeTab.Home),
    val homeRows: List<HomeRow> = emptyList(),
    /** e.g. "31 games · 14 apps · SD 87 GB free · Internal 52 GB free · scanned 14:05" */
    val statusLine: String = "",
    val selectedTab: Int = 0,
    val layout: HomeLayout = HomeLayout.Carousel,
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
    val layout: HomeLayout = HomeLayout.Carousel,
    val focusByTab: Map<String, FocusMemory> = emptyMap(),
    val tabDirection: Int = 1,
    val hasAccess: Boolean = true,
    val accessDismissed: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as LighthouseApp).repository
    private val launcher = GameLauncher(app)

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

    fun openApp(pkg: String) {
        getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { getApplication<Application>().startActivity(it) }
    }
    private val prefs = MutableStateFlow(UiPrefs(hasAccess = repo.hasStorageAccess()))

    val state: StateFlow<HomeState> = combine(repo.games, repo.favourites, repo.plays, repo.scanInfo, prefs) { games, favKeys, plays, scan, p ->
        val ui = games.map { it.toUi() }
        val byPlatform = ui.groupBy { it.platformId }
        val byKey = ui.associateBy { it.key }
        val platformTabs = PlatformCatalog.all
            .filter { it.id == PlatformCatalog.ANDROID || byPlatform[it.id].orEmpty().isNotEmpty() }
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
            recentlyPlayed = plays.map { it.gameKey }.filter { key -> games.firstOrNull { it.key == key }?.isGame == true },
            recentlyAdded = games.filter { System.currentTimeMillis() - it.addedAt < RECENT_WINDOW_MS }
                .sortedByDescending { it.addedAt }.map { it.key },
        )
        HomeState(
            tabs = tabs,
            homeRows = homeRows,
            statusLine = statusLine(games, scan),
            // Home always has something (at least the Platforms row), so it is the natural starting tab.
            selectedTab = tabs.indexOfFirst { it.key == (p.selectedKey ?: HomeTab.Home.key) }.coerceAtLeast(0),
            layout = p.layout,
            favourites = favKeys.toSet(),
            focusByTab = p.focusByTab,
            gamesByTab = gamesByTab,
            tabDirection = p.tabDirection,
            showAccessPrompt = !p.hasAccess && !p.accessDismissed && repo.storageAccessIntent() != null,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** Called from Activity.onResume: re-check permission (it may have just been granted) and rescan. */
    fun onResume() {
        prefs.update { it.copy(hasAccess = repo.hasStorageAccess()) }
        repo.refreshAll()
    }

    fun storageAccessIntent(): Intent? = repo.storageAccessIntent()

    fun dismissAccessPrompt() = prefs.update { it.copy(accessDismissed = true) }

    private val _homePresses = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when the Home button is pressed while Lighthouse is already the foreground home app. */
    val homePresses: kotlinx.coroutines.flow.SharedFlow<Unit> = _homePresses

    /** Home button: back to the HOME tab, top-left item, like a console's home button. */
    fun onHomePressed() {
        prefs.update { it.copy(selectedKey = HomeTab.Home.key, tabDirection = -1, focusByTab = it.focusByTab - HomeTab.Home.key) }
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

    fun toggleLayout() = prefs.update {
        it.copy(layout = if (it.layout == HomeLayout.Carousel) HomeLayout.Grid else HomeLayout.Carousel)
    }

    fun onFocused(tab: HomeTab, index: Int) {
        val game = state.value.gamesFor(tab).getOrNull(index) ?: return
        val mem = FocusMemory(game.key, index)
        prefs.update { if (it.focusByTab[tab.key] == mem) it else it.copy(focusByTab = it.focusByTab + (tab.key to mem)) }
    }

    fun toggleFavourite(game: Game) = repo.toggleFavourite(game.key)

    fun recordLaunch(game: Game) = repo.recordLaunch(game.key)

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
): List<HomeRow> {
    fun games(keys: List<String>, limit: Int) = keys.mapNotNull { byKey[it] }.take(limit).map { HomeItem.GameItem(it) }
    val streaming = STREAMING_PACKAGES.mapNotNull { pkg -> byKey["${PlatformCatalog.ANDROID}|$pkg"] }.map { HomeItem.GameItem(it) }
    val platforms = tabs.mapIndexedNotNull { i, t ->
        (t as? HomeTab.PlatformTab)?.let { HomeItem.PlatformItem(i, it.platform, byPlatform[it.platform.id].orEmpty().size) }
    }
    return listOf(
        HomeRow("continue", "Continue playing", games(recentlyPlayed, 10), hero = true),
        HomeRow("favourites", "Favourites", games(favKeys, 30)),
        HomeRow("recent", "Recently added", games(recentlyAdded, 15)),
        HomeRow("streaming", "Streaming", streaming),
        HomeRow("platforms", "Platforms", platforms),
    ).filter { it.items.isNotEmpty() }
}

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

private fun GameEntity.toUi(): Game = when (kind) {
    GameKind.APP -> Game(key = key, platformId = platformId, title = title, subtitle = "Android app", packageName = locator)
    GameKind.ROM -> Game(
        key = key,
        platformId = platformId,
        title = title,
        subtitle = "${File(locator).extension.uppercase(Locale.ROOT)} · ${formatSize(sizeBytes)}",
        path = locator,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.UK, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.UK, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.UK, "%.0f KB", bytes / 1024.0)
}
