package io.cttech.lighthouse.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var db: LighthouseDatabase
    private val dao get() = db.library()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LighthouseDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    private fun rom(locator: String, title: String = locator, addedAt: Long = 1, size: Long = 10) = GameEntity(
        platformId = "ps2", kind = GameKind.ROM, locator = locator, title = title,
        sortTitle = title.lowercase(), sizeBytes = size, modifiedAt = addedAt, addedAt = addedAt,
    )

    @Test
    fun replaceGames_addsRemovesAndKeepsSurvivorIds() = runBlocking {
        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("a.iso"), rom("b.iso")))
        val first = dao.allGames().associateBy { it.locator }

        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("b.iso"), rom("c.iso")))
        val second = dao.allGames().associateBy { it.locator }

        assertEquals(setOf("b.iso", "c.iso"), second.keys)
        assertEquals(first.getValue("b.iso").id, second.getValue("b.iso").id)
    }

    @Test
    fun replaceGames_updatesChangedRowInPlace() = runBlocking {
        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("a.iso", size = 10)))
        val id = dao.allGames().single().id

        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("a.iso", size = 99)))

        val game = dao.allGames().single()
        assertEquals(id, game.id)
        assertEquals(99L, game.sizeBytes)
    }

    @Test
    fun replaceGames_leavesOtherPlatformsAndKindsAlone() = runBlocking {
        dao.insertGames(listOf(rom("keep.iso").copy(platformId = "psp"), rom("app").copy(kind = GameKind.APP)))

        dao.replaceGames("ps2", GameKind.ROM, emptyList())

        assertEquals(2, dao.allGames().size)
    }

    @Test
    fun replaceGames_withNoChangesDoesNotReEmit() = runBlocking {
        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("a.iso")))
        val before = dao.observeGames().first()

        dao.replaceGames("ps2", GameKind.ROM, listOf(rom("a.iso")))

        assertEquals(before, dao.observeGames().first())
    }

    @Test
    fun recordLaunch_countsAndKeepsLatestTime() = runBlocking {
        dao.recordLaunch("ps2|a.iso", 100)
        dao.recordLaunch("ps2|a.iso", 200)

        val play = dao.observePlays().first().single()
        assertEquals(2, play.launchCount)
        assertEquals(200L, play.lastPlayedAt)
    }

    @Test
    fun observePlays_mostRecentFirst() = runBlocking {
        dao.recordLaunch("ps2|old", 100)
        dao.recordLaunch("ps2|new", 300)

        assertEquals(listOf("ps2|new", "ps2|old"), dao.observePlays().first().map { it.gameKey })
    }

    @Test
    fun putCover_replacesAndDeleteRemoves() = runBlocking {
        dao.putCover(CoverEntity("ps2|a.iso", "/one.png", "test", 1))
        dao.putCover(CoverEntity("ps2|a.iso", "/two.png", "test", 2))
        assertEquals(1, dao.covers().size)

        dao.deleteCover("ps2|a.iso")
        assertTrue(dao.covers().isEmpty())
    }
}
