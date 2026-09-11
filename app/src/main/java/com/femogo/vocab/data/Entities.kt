package com.femogo.vocab.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.Cefr
import com.femogo.vocab.engine.Pos
import com.femogo.vocab.engine.Word

/**
 * Un juego con su propio vocabulario y su propio progreso: las palabras
 * frecuentes, los verbos compuestos, o cualquier otro que se publique después.
 *
 * Los módulos son datos, no código. Añadir uno nuevo es publicar un archivo y
 * nombrarlo en el índice; la aplicación instalada lo encuentra al actualizar y
 * no hace falta compilar nada.
 */
@Entity(tableName = "modulos")
data class ModuloEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val descripcion: String,
    /** Versión publicada del contenido, para saber si hay que volver a bajarlo. */
    val version: Int,
    val palabras: Int
)

/**
 * El diccionario. La clave es el módulo más la posición en su lista de
 * frecuencia, así que dos módulos pueden usar los mismos números sin pisarse.
 */
@Entity(tableName = "words", primaryKeys = ["modulo", "rank"])
data class WordEntity(
    val modulo: String,
    val rank: Int,
    val en: String,
    val lemma: String,
    val pos: String,
    val es: String,
    val esAlt: String,
    val cefr: String,
    val hint: String?
) {
    fun toDomain(): Word? {
        val p = Pos.from(pos) ?: return null
        val c = Cefr.from(cefr) ?: return null
        return Word(
            rank = rank,
            en = en,
            lemma = lemma,
            pos = p,
            es = es,
            esAlt = esAlt.split(';').filter { it.isNotBlank() },
            cefr = c,
            hint = hint
        )
    }

    companion object {
        fun from(w: Word, modulo: String) = WordEntity(
            modulo = modulo,
            rank = w.rank,
            en = w.en,
            lemma = w.lemma,
            pos = w.pos.name.lowercase(),
            es = w.es,
            esAlt = w.esAlt.joinToString(";"),
            cefr = w.cefr.name,
            hint = w.hint
        )
    }
}

/**
 * El progreso sobre una palabra. Vive en una tabla aparte del diccionario para
 * que reimportar o ampliar el vocabulario no borre lo aprendido.
 *
 * No se guarda ninguna fecha. [Card.dueTurn] cuenta preguntas respondidas, no
 * tiempo: el juego va al ritmo de quien juega, y una semana sin abrirlo no
 * cambia nada.
 */
@Entity(tableName = "cards", primaryKeys = ["modulo", "rank"])
data class CardEntity(
    val modulo: String,
    val rank: Int,
    val box: Int,
    val dueTurn: Int,
    val seen: Int,
    val correct: Int,
    val streak: Int
) {
    fun toDomain() = Card(rank, box, dueTurn, seen, correct, streak)

    companion object {
        fun from(card: Card, modulo: String) = CardEntity(
            modulo = modulo,
            rank = card.rank,
            box = card.box,
            dueTurn = card.dueTurn,
            seen = card.seen,
            correct = card.correct,
            streak = card.streak
        )
    }
}
