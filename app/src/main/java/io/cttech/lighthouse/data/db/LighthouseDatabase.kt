package io.cttech.lighthouse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SourceEntity::class, GameEntity::class, FavouriteEntity::class], version = 1)
abstract class LighthouseDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context): LighthouseDatabase =
            Room.databaseBuilder(context, LighthouseDatabase::class.java, "lighthouse.db").build()
    }
}
