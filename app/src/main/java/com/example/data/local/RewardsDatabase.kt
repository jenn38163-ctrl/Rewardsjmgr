package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        JournalEntryEntity::class,
        LedgerEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RewardsDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: RewardsDatabase? = null

        fun getInstance(context: Context): RewardsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RewardsDatabase::class.java,
                    "rewards_double_entry.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
