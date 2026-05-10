package com.calltheitguy.monitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ServerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MonitorDatabase : RoomDatabase() {

    abstract fun serverDao(): ServerDao

    companion object {
        private const val DATABASE_NAME = "monitor.db"

        @Volatile
        private var instance: MonitorDatabase? = null

        fun getInstance(context: Context): MonitorDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonitorDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { database ->
                        instance = database
                    }
            }
        }
    }
}
