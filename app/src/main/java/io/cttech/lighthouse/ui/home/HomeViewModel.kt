package io.cttech.lighthouse.ui.home

import androidx.lifecycle.ViewModel
import io.cttech.lighthouse.data.SampleLibrary
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.HomeLayout
import io.cttech.lighthouse.model.HomeTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeState(
    val tabs: List<HomeTab>,
    val selectedTab: Int,
    val layout: HomeLayout,
    val favourites: Set<String>,
    /** Last focused item per tab key, so switching tabs returns you to where you were. */
    val focusByTab: Map<String, Int>,
    val games: List<Game>,
    /** +1 / -1: direction of the last tab move, for the slide animation (survives wrap-around). */
    val tabDirection: Int = 1,
) {
    val currentTab: HomeTab get() = tabs[selectedTab]

    fun gamesFor(tab: HomeTab): List<Game> = when (tab) {
        HomeTab.Favourites -> favourites.mapNotNull { id -> games.firstOrNull { it.id == id } }
        is HomeTab.PlatformTab -> games.filter { it.platformId == tab.platform.id }
    }

    fun focusIndexFor(tab: HomeTab): Int =
        (focusByTab[tab.key] ?: 0).coerceIn(0, (gamesFor(tab).size - 1).coerceAtLeast(0))
}

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        HomeState(
            tabs = listOf(HomeTab.Favourites) + SampleLibrary.platforms.map { HomeTab.PlatformTab(it) },
            selectedTab = 0,
            layout = HomeLayout.Carousel,
            favourites = SampleLibrary.initialFavourites,
            focusByTab = emptyMap(),
            games = SampleLibrary.games,
        ),
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Moves one tab left/right, wrapping around like a console shelf. */
    fun moveTab(delta: Int) = _state.update {
        it.copy(selectedTab = (it.selectedTab + delta).mod(it.tabs.size), tabDirection = delta.coerceIn(-1, 1))
    }

    fun toggleLayout() = _state.update {
        it.copy(layout = if (it.layout == HomeLayout.Carousel) HomeLayout.Grid else HomeLayout.Carousel)
    }

    fun onFocused(tab: HomeTab, index: Int) = _state.update {
        if (it.focusByTab[tab.key] == index) it else it.copy(focusByTab = it.focusByTab + (tab.key to index))
    }

    fun toggleFavourite(game: Game) = _state.update {
        val favs = if (game.id in it.favourites) it.favourites - game.id else it.favourites + game.id
        it.copy(favourites = favs)
    }
}
