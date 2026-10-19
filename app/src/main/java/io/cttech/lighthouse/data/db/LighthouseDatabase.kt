package io.cttech.lighthouse.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, GameEntity::class, FavouriteEntity::class, PlayEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // v2: plays table
)
abstract class LighthouseDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context): LighthouseDatabase =
            Room.databaseBuilder(context, LighthouseDatabase::class.java, "lighthouse.db").build()
    }
}
