package com.nagorithm.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StoredActivity::class,
        Profile::class,
        Goal::class,
        SpokenNag::class,
        Note::class,
    ],
    version = 7,
    exportSchema = true,
    // Tables and a defaulted column only, so Room can write all of these unaided.
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class NagorithmDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao

    abstract fun profileDao(): ProfileDao

    abstract fun goalDao(): GoalDao

    abstract fun spokenNagDao(): SpokenNagDao

    abstract fun noteDao(): NoteDao

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
