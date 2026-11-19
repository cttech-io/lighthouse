package io.cttech.lighthouse.ui.home

import io.cttech.lighthouse.library.PlatformCatalog
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.GameCollection
import io.cttech.lighthouse.model.HomeItem
import io.cttech.lighthouse.model.HomeTab
import io.cttech.lighthouse.system.HOME_SECTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {
    private val ps1 = PlatformCatalog.byId("ps1")!!
    private fun game(n: Int, platform: String = "ps1") = Game(key = "$platform|g$n", platformId = platform, title = "Game $n")

    private val games = (1..4).map { game(it) }
    private val byKey = games.associateBy { it.key }
    private val byPlatform = mapOf("ps1" to games)
    private val tabs: List<HomeTab> = listOf(HomeTab.Home, HomeTab.PlatformTab(ps1))

    private fun rows(
        favKeys: List<String> = emptyList(),
        recentlyPlayed: List<String> = emptyList(),
        recentlyAdded: List<String> = emptyList(),
        order: List<String> = HOME_SECTIONS,
        hidden: Set<String> = emptySet(),
        collections: List<GameCollection> = emptyList(),
    ) = buildHomeRows(tabs, byKey, byPlatform, favKeys, recentlyPlayed, recentlyAdded, order, hidden, collections)

    @Test fun emptyRowsAreSkipped() {
        // Nothing favourited, played or recently added: only the Platforms row has content.
        assertEquals(listOf("platforms"), rows().map { it.id })
    }

    @Test fun followsTheUsersOrder() {
        val result = rows(favKeys = listOf("ps1|g1"), recentlyAdded = listOf("ps1|g2"), order = listOf("platforms", "recent", "favourites"))
        assertEquals(listOf("platforms", "recent", "favourites"), result.map { it.id })
    }

    @Test fun hiddenSectionsAreLeftOut() {
        val result = rows(favKeys = listOf("ps1|g1"), recentlyAdded = listOf("ps1|g2"), hidden = setOf("favourites"))
        assertEquals(listOf("recent", "platforms"), result.map { it.id })
    }

    @Test fun theFirstRowGetsTheLargeCovers() {
        val result = rows(favKeys = listOf("ps1|g1"), recentlyAdded = listOf("ps1|g2"))
        assertEquals(listOf("favourites", "recent", "platforms"), result.map { it.id })
        assertEquals(listOf(true, false, false), result.map { it.hero })
    }

    @Test fun thePlatformsRowIsNeverTheHero() {
        // Only Platforms is left (everything else empty or hidden): it keeps the normal size.
        val result = rows(favKeys = listOf("ps1|g1"), hidden = setOf("favourites"))
        assertEquals(listOf("platforms"), result.map { it.id })
        assertFalse(result.single().hero)
    }

    @Test fun platformTilesPointAtTheirTabWithAGameCount() {
        val item = rows().single().items.single() as HomeItem.PlatformItem
        assertEquals(1, item.tabIndex) // after the Home tab
        assertEquals(4, item.count)
    }

    @Test fun keysThatNoLongerMatchAGameAreIgnoredAndRowsAreCapped() {
        val many = (1..40).map { game(it, "gc") }
        val manyByKey = byKey + many.associateBy { it.key }
        val result = buildHomeRows(tabs, manyByKey, byPlatform, many.map { it.key } + "gone|x", emptyList(), emptyList(), listOf("favourites"), emptySet())
        val favourites = result.single()
        assertEquals(30, favourites.items.size)
        assertTrue(favourites.items.none { it.key == "gone|x" })
    }

    @Test fun streamingClientsGroupTogether() {
        val moonlight = Game(key = "android|com.limelight", platformId = "android", title = "Moonlight", packageName = "com.limelight")
        val other = Game(key = "android|com.example", platformId = "android", title = "Example", packageName = "com.example")
        val result = buildHomeRows(tabs, byKey + (moonlight.key to moonlight) + (other.key to other), byPlatform, emptyList(), emptyList(), emptyList(), listOf("streaming"), emptySet())
        assertEquals(listOf("android|com.limelight"), result.single().items.map { it.key })
    }

    @Test fun cloudAndRemotePlayAppsJoinTheStreamingRow() {
        val apps = listOf("com.nvidia.geforcenow", "com.microsoft.xboxone.smartglass", "com.playstation.remoteplay", "psplay.grill.com")
            .map { Game(key = "android|$it", platformId = "android", title = it, packageName = it) }
        val result = buildHomeRows(tabs, byKey + apps.associateBy { it.key }, byPlatform, emptyList(), emptyList(), emptyList(), listOf("streaming"), emptySet())
        assertEquals(apps.map { it.key }, result.single().items.map { it.key })
    }

    @Test fun collectionsAreRowsInTheirPlaceAlphabetical() {
        val coop = GameCollection(4, "Co-op", listOf("ps1|g3", "ps1|gone", "ps1|g1"))
        val result = rows(order = listOf("collection:4", "platforms"), collections = listOf(coop))
        assertEquals(listOf("collection:4", "platforms"), result.map { it.id })
        assertEquals("Co-op", result[0].title)
        assertTrue(result[0].hero) // first row shown, so large covers
        // Alphabetical, and a game no longer in the library ("gone") is skipped.
        assertEquals(listOf("ps1|g1", "ps1|g3"), result[0].items.map { it.key })
    }

    @Test fun emptyOrHiddenCollectionsAreLeftOut() {
        val empty = GameCollection(1, "Empty", emptyList())
        val rpgs = GameCollection(2, "RPGs", listOf("ps1|g2"))
        val result = rows(order = listOf("collection:1", "collection:2", "platforms"), hidden = setOf("collection:2"), collections = listOf(empty, rpgs))
        assertEquals(listOf("platforms"), result.map { it.id })
    }
}
