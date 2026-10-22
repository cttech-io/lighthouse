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
}
