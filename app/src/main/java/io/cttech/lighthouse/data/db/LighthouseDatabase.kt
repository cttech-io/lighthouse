package io.cttech.lighthouse.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, GameEntity::class, FavouriteEntity::class, PlayEntity::class, CoverEntity::class, HiddenEntity::class, SessionEntity::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // v2: plays table
        AutoMigration(from = 2, to = 3), // v3: games.isGame
        AutoMigration(from = 3, to = 4), // v4: covers table
        AutoMigration(from = 4, to = 5), // v5: hidden table
        AutoMigration(from = 5, to = 6), // v6: sources.ignored
        AutoMigration(from = 6, to = 7), // v7: sessions table (play time)
    ],
)
abstract class LighthouseDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context): LighthouseDatabase =
            Room.databaseBuilder(context, LighthouseDatabase::class.java, "lighthouse.db").build()
    }
}
