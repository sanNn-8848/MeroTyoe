package com.romannepali.keyboard.dictionary.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for storing and retrieving frequently used Nepali phrases.
 */
@Dao
interface NepaliPhraseDao {

    @Query("SELECT * FROM nepali_phrases ORDER BY frequency DESC, timestamp DESC")
    fun getAllPhrases(): Flow<List<NepaliPhraseEntity>>

    @Query("SELECT * FROM nepali_phrases WHERE category = :category ORDER BY frequency DESC")
    fun getPhrasesByCategory(category: String): Flow<List<NepaliPhraseEntity>>

    @Query("SELECT * FROM nepali_phrases WHERE phrase LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun searchPhrases(prefix: String, limit: Int = 10): List<NepaliPhraseEntity>

    @Query("SELECT * FROM nepali_phrases ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopPhrases(limit: Int = 20): List<NepaliPhraseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(phrase: NepaliPhraseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(phrases: List<NepaliPhraseEntity>)

    @Query("UPDATE nepali_phrases SET frequency = frequency + 1, timestamp = :timestamp WHERE phrase = :phrase")
    suspend fun incrementFrequency(phrase: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM nepali_phrases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM nepali_phrases WHERE phrase = :phrase")
    suspend fun deleteByPhrase(phrase: String)

    @Query("SELECT COUNT(*) FROM nepali_phrases")
    suspend fun getCount(): Int
}
