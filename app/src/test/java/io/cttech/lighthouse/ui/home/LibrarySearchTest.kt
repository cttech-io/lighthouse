package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    private fun game(title: String, platform: String = "ps2") = Game(key = "$platform|$title", platformId = platform, title = title)

    private val library = listOf(
        game("Super Mario 64", "n64"),
        game("Mario Kart 64", "n64"),
        game("Dr. Mario", "nes"),
        game("Marionette Handler", "ps1"),
        game("The Legend of Zelda", "nes"),
        game("Pokémon Emerald", "gba"),
        game("Gran Turismo 4"),
    )

    private fun titles(
        filters: SearchFilters,
        favourites: Set<String> = emptySet(),
        played: Set<String> = emptySet(),
    ) = searchLibrary(library, filters, favourites, played).map { it.title }

    @Test fun nothingTypedListsEverythingAlphabetically() {
        // "The Legend of Zelda" sorts under L, as on the shelves.
        assertEquals(
            listOf("Dr. Mario", "Gran Turismo 4", "The Legend of Zelda", "Mario Kart 64", "Marionette Handler", "Pokémon Emerald", "Super Mario 64"),
            titles(SearchFilters()),
        )
    }

    @Test fun titleStartsFirstThenWordStartsThenAnywhere() {
        assertEquals(
            listOf("Mario Kart 64", "Marionette Handler", "Dr. Mario", "Super Mario 64"),
            titles(SearchFilters(query = "mario")),
        )
        assertEquals(listOf("Pokémon Emerald"), titles(SearchFilters(query = "merald")))
    }

    @Test fun everyWordMustMatchInAnyOrder() {
        assertEquals(listOf("Mario Kart 64", "Super Mario 64"), titles(SearchFilters(query = "64 mario")))
        assertEquals(emptyList<String>(), titles(SearchFilters(query = "mario zelda")))
    }

    @Test fun ignoresCaseAccentsPunctuationAndALeadingThe() {
        assertEquals(listOf("Pokémon Emerald"), titles(SearchFilters(query = "POKEMON")))
        assertEquals(listOf("Dr. Mario"), titles(SearchFilters(query = "dr mario")))
        assertEquals(listOf("The Legend of Zelda"), titles(SearchFilters(query = "the legend")))
        assertEquals(titles(SearchFilters()), titles(SearchFilters(query = "  ")))
    }

    @Test fun platformFilter() {
        assertEquals(listOf("Mario Kart 64", "Super Mario 64"), titles(SearchFilters(platformId = "n64")))
        assertEquals(listOf("Dr. Mario"), titles(SearchFilters(query = "mario", platformId = "nes")))
    }

    @Test fun playedAndUnplayed() {
        val played = setOf("n64|Super Mario 64", "ps2|Gran Turismo 4")
        assertEquals(listOf("Gran Turismo 4", "Super Mario 64"), titles(SearchFilters(played = PlayedFilter.Played), played = played))
        assertEquals(listOf("Mario Kart 64", "Marionette Handler", "Dr. Mario"), titles(SearchFilters(query = "mario", played = PlayedFilter.Unplayed), played = played))
    }

    @Test fun favouritesOnly() {
        val favs = setOf("nes|The Legend of Zelda", "gba|Pokémon Emerald")
        assertEquals(listOf("The Legend of Zelda", "Pokémon Emerald"), titles(SearchFilters(favouritesOnly = true), favourites = favs))
    }

    @Test fun anyFilterIgnoresTheQuery() {
        assertEquals(false, SearchFilters(query = "mario").anyFilter)
        assertEquals(true, SearchFilters(favouritesOnly = true).anyFilter)
    }
}
