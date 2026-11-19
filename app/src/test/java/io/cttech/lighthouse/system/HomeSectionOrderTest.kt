package io.cttech.lighthouse.system

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionOrderTest {
    @Test fun noSavedOrderGivesTheDefault() {
        assertEquals(HOME_SECTIONS, mergeHomeSectionOrder(emptyList()))
    }

    @Test fun aCompleteSavedOrderIsKept() {
        val saved = listOf("platforms", "moonlight", "streaming", "recent", "continue", "favourites")
        assertEquals(saved, mergeHomeSectionOrder(saved))
    }

    @Test fun aSectionAddedLaterGoesAfterItsDefaultNeighbour() {
        // "moonlight" is missing from an order saved before it existed: it belongs after "streaming".
        val saved = listOf("platforms", "streaming", "favourites", "continue", "recent")
        assertEquals(listOf("platforms", "streaming", "moonlight", "favourites", "continue", "recent"), mergeHomeSectionOrder(saved))
    }

    @Test fun aNewFirstSectionGoesFirst() {
        // "favourites" has no default neighbour before it, so it leads rather than trailing.
        val saved = listOf("continue", "recent", "streaming", "moonlight", "platforms")
        assertEquals(listOf("favourites", "continue", "recent", "streaming", "moonlight", "platforms"), mergeHomeSectionOrder(saved))
    }

    @Test fun unknownIdsAreDropped() {
        assertEquals(HOME_SECTIONS, mergeHomeSectionOrder(HOME_SECTIONS + "retired-section"))
    }

    @Test fun savedCollectionsAreKept() {
        val saved = listOf("favourites", "collection:3", "continue", "recent", "streaming", "moonlight", "platforms")
        assertEquals(saved, mergeHomeSectionOrder(saved))
    }

    @Test fun deletedCollectionsLeaveAndNewOnesFollowTheLastCollection() {
        val order = listOf("continue", "collection:1", "collection:2", "favourites", "platforms")
        // 2 was deleted; 5 is new, so it goes where 1 is (the last collection still there).
        assertEquals(
            listOf("continue", "collection:1", "collection:5", "favourites", "platforms"),
            withCollections(order, listOf("collection:1", "collection:5")),
        )
    }

    @Test fun theFirstCollectionGoesAfterFavourites() {
        assertEquals(
            listOf("favourites", "collection:7", "continue", "recent", "streaming", "moonlight", "platforms"),
            withCollections(HOME_SECTIONS, listOf("collection:7")),
        )
        assertEquals(HOME_SECTIONS, withCollections(HOME_SECTIONS + "collection:9", emptyList()))
    }
}
