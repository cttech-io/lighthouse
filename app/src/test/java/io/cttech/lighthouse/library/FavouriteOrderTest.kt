package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavouriteOrderTest {
    private val all = listOf("a", "b", "c", "d")

    @Test fun movesOnePlaceEitherWay() {
        assertEquals(listOf("b", "a", "c", "d"), FavouriteOrder.move(all, all, "a", +1))
        assertEquals(listOf("a", "b", "d", "c"), FavouriteOrder.move(all, all, "d", -1))
    }

    @Test fun stopsAtTheEnds() {
        assertNull(FavouriteOrder.move(all, all, "a", -1))
        assertNull(FavouriteOrder.move(all, all, "d", +1))
    }

    @Test fun favouritesHomeDoesntShowKeepTheirPlaces() {
        // "b" is hidden: moving "a" right passes "c", the next card you can see, and "b" stays second.
        assertEquals(listOf("c", "b", "a", "d"), FavouriteOrder.move(all, listOf("a", "c", "d"), "a", +1))
    }

    @Test fun aCardNotOnTheRowDoesntMove() = assertNull(FavouriteOrder.move(all, listOf("a", "c"), "b", +1))
}
