package com.romannepali.keyboard.dictionary.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for persistent local Nepali dictionary storage.
 */
@Database(
    entities = [NepaliWordEntity::class, NepaliPhraseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NepaliWordDatabase : RoomDatabase() {

    abstract fun wordDao(): NepaliWordDao
    abstract fun phraseDao(): NepaliPhraseDao

    companion object {
        @Volatile
        private var INSTANCE: NepaliWordDatabase? = null

        fun getDatabase(context: Context): NepaliWordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NepaliWordDatabase::class.java,
                    "nepali_word_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
