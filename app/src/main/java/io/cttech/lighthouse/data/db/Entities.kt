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

enum class GameKind { ROM, APP }

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

/** Launch history, keyed like favourites. Drives "Continue playing" (and play-time tracking later). */
@Entity(tableName = "plays")
data class PlayEntity(
    @PrimaryKey val gameKey: String,
    val lastPlayedAt: Long,
    val launchCount: Int,
)

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

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val gameKey: String,
    val addedAt: Long,
)
