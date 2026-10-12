package com.romannepali.keyboard.dictionary.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing frequently used Nepali phrases for predictive text.
 */
@Entity(
    tableName = "nepali_phrases",
    indices = [Index(value = ["phrase"], unique = true)]
)
data class NepaliPhraseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phrase: String,
    val devanagariPhrase: String,
    val category: String = "general",
    val frequency: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
