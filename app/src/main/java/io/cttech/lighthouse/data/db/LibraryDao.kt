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
            val merged = s.copy(id = old.id, addedAt = old.addedAt)
            if (merged != old) merged else null
        }
        // Only write when something changed: every write re-emits the observed queries.
        if (removed.isNotEmpty()) deleteGames(removed)
        if (added.isNotEmpty()) insertGames(added)
        if (changed.isNotEmpty()) updateGames(changed)
    }

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(f: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE gameKey = :key")
    suspend fun removeFavourite(key: String)

    @Query("SELECT COUNT(*) FROM favourites WHERE gameKey = :key")
    suspend fun isFavourite(key: String): Int
}
