package io.cttech.lighthouse.covers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGridDbTest {
    private val image = byteArrayOf(1, 2, 3)

    /** A fake API: [routes] maps a URL fragment to the body returned for it; anything else fails (null). */
    private fun api(vararg routes: Pair<String, String?>, images: Boolean = true) = SteamGridDb("key") { url, _ ->
        when {
            images && url.startsWith("https://cdn") -> image
            else -> routes.firstOrNull { (fragment, _) -> fragment in url }?.second?.toByteArray()
        }
    }

    @Test fun nonJsonResponsesAreNothingFoundNotACrash() {
        val portal = "<html><body>Sign in to the hotel Wi-Fi</body></html>"
        val sgdb = api("/search/" to portal, "/grids/" to portal)
        assertEquals(emptyList<SteamGridDb.SearchHit>(), sgdb.search("Zelda"))
        assertEquals(emptyList<SteamGridDb.Grid>(), sgdb.grids(gameId = 1))
        assertNull(sgdb.forTitle("Zelda"))
        assertNull(sgdb.forSteamApp(220))
        assertFalse(sgdb.isValid())
    }

    @Test fun missingOrWrongTypedFieldsAreSkipped() {
        val search = """{"success":true,"data":[{"name":"No id"},{"id":"x","name":"Bad id"},{"id":7},42,{"id":9,"name":"Good","release_date":1262304000}]}"""
        val hits = api("/search/" to search).search("anything")
        assertEquals(listOf(SteamGridDb.SearchHit(9, "Good", 2010)), hits)
    }

    @Test fun gridsWithoutUrlAreSkippedAndThumbFallsBackToUrl() {
        val grids = """{"data":[{"thumb":"t"},{"url":"https://cdn/a.png","style":"alternate"}]}"""
        assertEquals(listOf(SteamGridDb.Grid("https://cdn/a.png", "https://cdn/a.png", "alternate")), api("/grids/" to grids).grids(steamAppId = 220))
    }

    @Test fun forTitleOnlyTakesAConfidentMatch() {
        val search = """{"data":[{"id":1,"name":"Pokemon Ultra Violet"},{"id":2,"name":"The Legend of Zelda: The Minish Cap"}]}"""
        val grids = """{"data":[{"url":"https://cdn/zelda.png"}]}"""
        val sgdb = api("/search/" to search, "/grids/game/2" to grids)
        assertArrayEquals(image, sgdb.forTitle("The Legend of Zelda: The Minish Cap"))
        assertNull(sgdb.forTitle("Golden Sun"))
    }

    @Test fun emptyDataAndFailedRequests() {
        assertNull(api("/grids/" to """{"data":[]}""").forSteamApp(220))
        assertEquals(emptyList<SteamGridDb.SearchHit>(), api().search("x"))
        assertTrue(api("/search/" to """{"success":true,"data":[]}""").isValid())
    }
}
