package com.powertrack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.powertrack.app.data.model.LocationEntity

@Database(
    entities = [LocationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PowerTrackDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: PowerTrackDatabase? = null

        fun getInstance(context: Context): PowerTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PowerTrackDatabase::class.java,
                    "powertrack_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
