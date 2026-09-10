package com.femogo.vocab.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY rank")
    suspend fun all(): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("DELETE FROM words")
    suspend fun clear()
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    suspend fun all(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE rank = :rank")
    suspend fun byRank(rank: Int): CardEntity?

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Query("DELETE FROM cards")
    suspend fun clear()
}
