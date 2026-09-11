package com.femogo.vocab.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.Cefr
import com.femogo.vocab.engine.Pos
import com.femogo.vocab.engine.Word

/**
 * El diccionario. Se rellena desde el asset la primera vez y se puede sustituir
 * por completo cuando el usuario importa una versión ampliada.
 */
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val rank: Int,
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
        fun from(w: Word) = WordEntity(
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
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val rank: Int,
    val box: Int,
    val dueAt: Long,
    val seen: Int,
    val correct: Int,
    val streak: Int,
    val lastSeenAt: Long,
    /** Cuándo se respondió por primera vez. Dato informativo: nada lo usa para decidir. */
    val introducedAt: Long
) {
    fun toDomain() = Card(rank, box, dueAt, seen, correct, streak, lastSeenAt)

    companion object {
        fun from(card: Card, introducedAt: Long) = CardEntity(
            rank = card.rank,
            box = card.box,
            dueAt = card.dueAt,
            seen = card.seen,
            correct = card.correct,
            streak = card.streak,
            lastSeenAt = card.lastSeenAt,
            introducedAt = introducedAt
        )
    }
}
