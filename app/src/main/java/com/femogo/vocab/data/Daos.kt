package com.femogo.vocab.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ModuloDao {
    @Query("SELECT * FROM modulos ORDER BY nombre")
    suspend fun todos(): List<ModuloEntity>

    @Query("SELECT * FROM modulos WHERE id = :id")
    suspend fun porId(id: String): ModuloEntity?

    @Upsert
    suspend fun guardar(modulo: ModuloEntity)

    @Query("DELETE FROM modulos WHERE id = :id")
    suspend fun borrar(id: String)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE modulo = :modulo ORDER BY rank")
    suspend fun delModulo(modulo: String): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE modulo = :modulo")
    suspend fun cuantas(modulo: String): Int

    @Query("SELECT COUNT(*) FROM words")
    suspend fun total(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE modulo = :modulo")
    suspend fun limpiar(modulo: String)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE modulo = :modulo")
    suspend fun delModulo(modulo: String): List<CardEntity>

    @Query("SELECT * FROM cards")
    suspend fun todas(): List<CardEntity>

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Query("DELETE FROM cards WHERE modulo = :modulo")
    suspend fun limpiar(modulo: String)

    @Query("DELETE FROM cards")
    suspend fun limpiarTodo()
}
