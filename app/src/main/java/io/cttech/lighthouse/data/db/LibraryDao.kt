package io.cttech.lighthouse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM games ORDER BY sortTitle")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM favourites ORDER BY addedAt")
    fun observeFavourites(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM games WHERE platformId = :platformId AND kind = :kind")
    suspend fun games(platformId: String, kind: GameKind): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGames(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun deleteGames(ids: List<Long>)

    /** Makes the stored games for one platform+kind match [scanned], keeping ids and addedAt for survivors. */
    @Transaction
    suspend fun replaceGames(platformId: String, kind: GameKind, scanned: List<GameEntity>) {
        val existing = games(platformId, kind).associateBy { it.locator }
        val incoming = scanned.associateBy { it.locator }
        val removed = existing.values.filter { it.locator !in incoming }.map { it.id }
        val added = scanned.filter { it.locator !in existing }
        val changed = scanned.mapNotNull { s ->
            val old = existing[s.locator] ?: return@mapNotNull null
            val merged = s.copy(id = old.id) // addedAt comes from the file/app itself, so it's stable
            if (merged != old) merged else null
        }
        // Only write when something changed: every write re-emits the observed queries.
        if (removed.isNotEmpty()) deleteGames(removed)
        if (added.isNotEmpty()) insertGames(added)
        if (changed.isNotEmpty()) updateGames(changed)
    }

    @Query("SELECT * FROM plays ORDER BY lastPlayedAt DESC")
    fun observePlays(): Flow<List<PlayEntity>>

    @Query("INSERT INTO plays (gameKey, lastPlayedAt, launchCount) VALUES (:key, :at, 1) ON CONFLICT(gameKey) DO UPDATE SET lastPlayedAt = :at, launchCount = launchCount + 1")
    suspend fun recordLaunch(key: String, at: Long)

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL")
    suspend fun openSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET endedAt = :endedAt, playedMs = :playedMs WHERE id = :id")
    suspend fun finishSession(id: Long, endedAt: Long, playedMs: Long?)

    @Query("SELECT gameKey, SUM(playedMs) AS playedMs FROM sessions WHERE playedMs IS NOT NULL GROUP BY gameKey")
    fun observePlayTime(): Flow<List<PlayTimeTotal>>

    @Query("SELECT * FROM covers")
    fun observeCovers(): Flow<List<CoverEntity>>

    @Query("SELECT * FROM covers")
    suspend fun covers(): List<CoverEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCover(cover: CoverEntity)

    /** One write (one UI refresh) for a whole batch of covers. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCovers(covers: List<CoverEntity>)

    @Query("DELETE FROM covers WHERE gameKey = :key")
    suspend fun deleteCover(key: String)

    @Query("SELECT * FROM games")
    suspend fun allGames(): List<GameEntity>

    @Query("SELECT gameKey FROM hidden ORDER BY hiddenAt")
    fun observeHidden(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(h: HiddenEntity)

    @Query("DELETE FROM hidden WHERE gameKey = :key")
    suspend fun unhide(key: String)

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("SELECT * FROM sources ORDER BY platformId, path")
    fun observeSources(): Flow<List<SourceEntity>>

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT * FROM sources WHERE path = :path")
    suspend fun sourceByPath(path: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(f: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE gameKey = :key")
    suspend fun removeFavourite(key: String)

    @Query("SELECT COUNT(*) FROM favourites WHERE gameKey = :key")
    suspend fun isFavourite(key: String): Int
}
