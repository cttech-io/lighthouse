package io.cttech.lighthouse.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.cttech.lighthouse.LighthouseApp
import io.cttech.lighthouse.data.db.GameEntity
import io.cttech.lighthouse.data.db.GameKind
import io.cttech.lighthouse.library.PlatformCatalog
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
    val tabs: List<HomeTab> = listOf(HomeTab.Favourites),
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
    private val prefs = MutableStateFlow(UiPrefs(hasAccess = repo.hasStorageAccess()))

    val state: StateFlow<HomeState> = combine(repo.games, repo.favourites, prefs) { games, favKeys, p ->
        val ui = games.map { it.toUi() }
        val byPlatform = ui.groupBy { it.platformId }
        val byKey = ui.associateBy { it.key }
        val platformTabs = PlatformCatalog.all
            .filter { it.id == PlatformCatalog.ANDROID || byPlatform[it.id].orEmpty().isNotEmpty() }
            .map { HomeTab.PlatformTab(it) }
        val tabs = listOf(HomeTab.Favourites) + platformTabs
        val gamesByTab = buildMap {
            put(HomeTab.Favourites.key, favKeys.mapNotNull { byKey[it] })
            platformTabs.forEach { put(it.key, byPlatform[it.platform.id].orEmpty()) }
        }
        HomeState(
            tabs = tabs,
            selectedTab = (p.selectedKey ?: tabs.firstOrNull { gamesByTab[it.key].orEmpty().isNotEmpty() }?.key)
                .let { key -> tabs.indexOfFirst { it.key == key } }.coerceAtLeast(0),
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
