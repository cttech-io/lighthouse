package io.cttech.lighthouse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A folder Lighthouse scans for one platform. */
@Entity(tableName = "sources", indices = [Index(value = ["path"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    val path: String,
    val autoDetected: Boolean,
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
    val addedAt: Long,
) {
    val key: String get() = gameKey(platformId, locator)
}

fun gameKey(platformId: String, locator: String) = "$platformId|$locator"

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val gameKey: String,
    val addedAt: Long,
)
