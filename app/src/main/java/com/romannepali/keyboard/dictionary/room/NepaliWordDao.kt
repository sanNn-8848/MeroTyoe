package com.romannepali.keyboard.dictionary.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local Nepali dictionary operations.
 */
@Dao
interface NepaliWordDao {

    @Query("SELECT * FROM nepali_words WHERE isBlocked = 0 ORDER BY frequency DESC, word ASC")
    fun getAllActiveWords(): Flow<List<NepaliWordEntity>>

    @Query("SELECT * FROM nepali_words WHERE word LIKE :prefix || '%' AND isBlocked = 0 ORDER BY frequency DESC LIMIT :limit")
    suspend fun getWordsByPrefix(prefix: String, limit: Int = 12): List<NepaliWordEntity>

    @Query("SELECT * FROM nepali_words WHERE word = :word LIMIT 1")
    suspend fun findWord(word: String): NepaliWordEntity?

    @Query("SELECT * FROM nepali_words WHERE isPersonal = 1 ORDER BY timestamp DESC")
    fun getPersonalWords(): Flow<List<NepaliWordEntity>>

    @Query("SELECT * FROM nepali_words WHERE isLearned = 1 ORDER BY frequency DESC")
    fun getLearnedWords(): Flow<List<NepaliWordEntity>>

    @Query("SELECT * FROM nepali_words WHERE isBlocked = 1 ORDER BY timestamp DESC")
    fun getBlockedWords(): Flow<List<NepaliWordEntity>>

    @Query("SELECT * FROM nepali_words WHERE isBlocked = 0 ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int = 500): List<NepaliWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(word: NepaliWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<NepaliWordEntity>)

    @Query("DELETE FROM nepali_words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM nepali_words WHERE word = :word")
    suspend fun deleteByWord(word: String)

    @Query("DELETE FROM nepali_words WHERE isLearned = 1")
    suspend fun clearLearnedWords()

    @Query("SELECT COUNT(*) FROM nepali_words")
    suspend fun getCount(): Int

    @Query("UPDATE nepali_words SET frequency = frequency + 1, timestamp = :timestamp WHERE word = :word")
    suspend fun incrementFrequency(word: String, timestamp: Long = System.currentTimeMillis())
}
