package com.nagorithm.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StoredActivity::class, Profile::class, Goal::class],
    version = 4,
    exportSchema = true,
    // 4 only adds the goals table and a check-in timestamp, so Room can write it unaided.
    autoMigrations = [AutoMigration(from = 3, to = 4)],
)
abstract class NagorithmDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao

    abstract fun profileDao(): ProfileDao

    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var instance: NagorithmDatabase? = null

        fun get(context: Context): NagorithmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NagorithmDatabase::class.java,
                    "nagorithm.db",
                )
                    // Versions 1 and 2 never left this machine and hold nothing worth keeping.
                    // From 3 onward the data is real: activity history reaches back further than
                    // Health Connect will re-serve, and the onboarding baseline is written once
                    // and cannot be recomputed, so every later change needs a real migration.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                    .also { instance = it }
            }
    }
}
