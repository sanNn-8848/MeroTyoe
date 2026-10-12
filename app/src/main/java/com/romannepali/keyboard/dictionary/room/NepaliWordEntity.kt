package com.romannepali.keyboard.dictionary.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a Nepali dictionary entry with both Romanized
 * phonetic form and authentic Devanagari script representation.
 */
@Entity(
    tableName = "nepali_words",
    indices = [Index(value = ["word"], unique = true)]
)
data class NepaliWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val devanagari: String,
    val frequency: Int = 10,
    val isPersonal: Boolean = false,
    val isLearned: Boolean = false,
    val isBlocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
