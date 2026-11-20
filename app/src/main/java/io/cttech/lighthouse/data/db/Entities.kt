package io.cttech.lighthouse.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A folder Lighthouse scans for one platform. [ignored]: the user removed an auto-detected folder; the row
 * stays so detection doesn't add it back.
 */
@Entity(tableName = "sources", indices = [Index(value = ["path"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    val path: String,
    val autoDetected: Boolean,
    @ColumnInfo(defaultValue = "0") val ignored: Boolean = false,
)

/**
 * STREAM: an app on a PC, played through Moonlight ([GameEntity.locator] = "<PC uuid>/<app id>").
 * SHORTCUT: a shortcut another app pinned to Lighthouse ([GameEntity.locator] = "<package>/<shortcut id>").
 */
enum class GameKind { ROM, APP, STREAM, SHORTCUT }

/**
 * One launchable thing. [locator] is a file path (ROM) or package name (APP); together with
 * [platformId] it forms the stable key used for favourites, so rescans never lose them.
 */
@Entity(tableName = "games", indices = [Index(value = ["platformId", "locator"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    val kind: GameKind,
    val locator: String,
    val title: String,
    val sortTitle: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    /** When it arrived on the device: file modified time (ROMs) or first install time (apps). */
    val addedAt: Long,
    /** Counts towards "Continue playing": ROMs, game apps, streaming clients, emulators. Utilities don't. */
    @ColumnInfo(defaultValue = "1") val isGame: Boolean = true,
) {
    val key: String get() = gameKey(platformId, locator)
}

fun gameKey(platformId: String, locator: String) = "$platformId|$locator"

/** Launch history, keyed like favourites. Drives "Continue playing"; play time is in [SessionEntity]. */
@Entity(tableName = "plays")
data class PlayEntity(
    @PrimaryKey val gameKey: String,
    val lastPlayedAt: Long,
    val launchCount: Int,
)

/**
 * One play session: from a launch until Lighthouse is back in front. [packageName] is the app that was started;
 * [playedMs] is how long it was really in front (Android's usage events), null while open or without Usage access.
 */
@Entity(tableName = "sessions", indices = [Index(value = ["gameKey"])])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameKey: String,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val playedMs: Long? = null,
)

/** Total measured play time for one game. */
data class PlayTimeTotal(val gameKey: String, val playedMs: Long)

/** Cover art for a game, keyed like favourites so rescans keep it. [source]: manual, steamgriddb, libretro. */
@Entity(tableName = "covers")
data class CoverEntity(
    @PrimaryKey val gameKey: String,
    val path: String,
    val source: String,
    val updatedAt: Long,
)

/** Items the user hid from Lighthouse (e.g. Gboard). Keyed like favourites; reversible in Settings. */
@Entity(tableName = "hidden")
data class HiddenEntity(
    @PrimaryKey val gameKey: String,
    val hiddenAt: Long,
)

/** A shelf of the user's own ("Co-op", "Pokémon"), shown as a HOME row. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** A game in a collection, keyed like favourites so rescans keep it. */
@Entity(tableName = "collection_items", primaryKeys = ["collectionId", "gameKey"], indices = [Index(value = ["gameKey"])])
data class CollectionItemEntity(
    val collectionId: Long,
    val gameKey: String,
    val addedAt: Long,
)

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val gameKey: String,
    /** Sort order: when it was favourited, or its place (0, 1, 2…) once favourites have been reordered. */
    val addedAt: Long,
)
