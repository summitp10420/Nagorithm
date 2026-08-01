package com.nagorithm.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StoredActivity::class],
    version = 1,
    exportSchema = false,
)
abstract class NagorithmDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao

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
                    // Acceptable while the schema is still moving and there are no real users.
                    // Must become real migrations before anyone else installs this.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
