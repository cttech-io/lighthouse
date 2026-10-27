package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.library.TitleCleaner
import io.cttech.lighthouse.model.Game

enum class PlayedFilter(val label: String) {
    Any("Played or not"),
    Played("Played"),
    Unplayed("Not played yet"),
}

/** What the Search page is showing: the typed text plus the filters. [platformId] null = every platform. */
data class SearchFilters(
    val query: String = "",
    val platformId: String? = null,
    val played: PlayedFilter = PlayedFilter.Any,
    val favouritesOnly: Boolean = false,
) {
    val anyFilter: Boolean get() = platformId != null || played != PlayedFilter.Any || favouritesOnly
}

/**
 * The games matching [filters], best match first. Every word typed must appear in the title (accents,
 * case, punctuation and a leading "The" ignored, as in [TitleCleaner.sortKey]); titles that start with the
 * first word come first, then titles with a word starting with it, then the rest, each alphabetically.
 * With nothing typed, everything that passes the filters is listed alphabetically.
 *
 * [played] holds the keys of games launched from Lighthouse at least once. [sortKeyOf] is there so the
 * caller can cache the titles' sort keys rather than rebuild them on every keystroke.
 */
internal fun searchLibrary(
    games: List<Game>,
    filters: SearchFilters,
    favourites: Set<String>,
    played: Set<String>,
    sortKeyOf: (Game) -> String = { TitleCleaner.sortKey(it.title) },
): List<Game> {
    val words = TitleCleaner.sortKey(filters.query).split(' ').filter { it.isNotEmpty() }
    val passing = games.filter { g ->
        (filters.platformId == null || g.platformId == filters.platformId) &&
            (!filters.favouritesOnly || g.key in favourites) &&
            when (filters.played) {
                PlayedFilter.Any -> true
                PlayedFilter.Played -> g.key in played
                PlayedFilter.Unplayed -> g.key !in played
            }
    }
    if (words.isEmpty()) return passing.sortedBy(sortKeyOf)
    return passing
        .map { it to sortKeyOf(it) }
        .filter { (_, title) -> words.all { it in title } }
        .sortedWith(compareBy({ (_, title) -> rank(title, words.first()) }, { (_, title) -> title }))
        .map { it.first }
}

/** 0: the title starts with [word]; 1: one of its words does; 2: it's only somewhere inside a word. */
private fun rank(title: String, word: String): Int = when {
    title.startsWith(word) -> 0
    title.contains(" $word") -> 1
    else -> 2
}
