package com.femogo.vocab.data

import android.content.Context
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.Word
import com.femogo.vocab.engine.WordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Puente entre la base de datos y el motor.
 *
 * El catálogo se mantiene en memoria una vez cargado. Aunque llegue a las 10000
 * palabras son unos pocos megabytes, y a cambio elegir distractores es un
 * filtrado sobre una lista en vez de una consulta por cada pregunta.
 */
class VocabRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private var cached: List<Word>? = null

    /** Carga el diccionario del asset la primera vez que se abre la aplicación. */
    suspend fun ensureSeeded(): Int = withContext(Dispatchers.IO) {
        if (db.wordDao().count() == 0) {
            context.assets.open(SEED_ASSET).use { importInto(it) }
        }
        db.wordDao().count()
    }

    suspend fun catalog(): List<Word> = cached ?: withContext(Dispatchers.IO) {
        db.wordDao().all().mapNotNull { it.toDomain() }.also { cached = it }
    }

    suspend fun cards(): Map<Int, Card> = withContext(Dispatchers.IO) {
        db.cardDao().all().associate { it.rank to it.toDomain() }
    }

    suspend fun save(card: Card, now: Long) = withContext(Dispatchers.IO) {
        // introducedAt se fija en la primera respuesta y no se vuelve a tocar:
        // es lo que sostiene el tope diario de palabras nuevas.
        val existing = db.cardDao().byRank(card.rank)
        db.cardDao().upsert(CardEntity.from(card, existing?.introducedAt ?: now))
    }

    /**
     * Sustituye el diccionario entero. El progreso no se toca: está en otra
     * tabla y se vuelve a enlazar por rank, así que ampliar el vocabulario no
     * cuesta lo aprendido.
     */
    suspend fun importInto(stream: InputStream): ImportReport = withContext(Dispatchers.IO) {
        val result = stream.bufferedReader().useLines { WordParser.parse(it) }
        if (result.words.isNotEmpty()) {
            db.wordDao().clear()
            result.words.chunked(500).forEach { batch ->
                db.wordDao().insertAll(batch.map { WordEntity.from(it) })
            }
            cached = null
        }
        ImportReport(imported = result.words.size, skipped = result.skipped.size)
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) { db.cardDao().clear() }

    data class ImportReport(val imported: Int, val skipped: Int)

    companion object {
        const val SEED_ASSET = "words.txt"
    }
}
