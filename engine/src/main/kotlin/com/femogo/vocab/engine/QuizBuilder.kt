package com.femogo.vocab.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Construye las preguntas de opción múltiple.
 *
 * El trabajo real está en elegir los distractores. Con opciones al azar el juego
 * se aprueba por eliminación —si la respuesta es la única que encaja
 * gramaticalmente, sobra saber la palabra— y el motor acaba registrando dominio
 * donde no lo hay. Por eso los distractores comparten categoría gramatical con
 * la respuesta y salen de una banda de frecuencia parecida.
 */
class QuizBuilder(
    private val random: Random = Random.Default,
    /** De cuántos vecinos por frecuencia se sortean los distractores. */
    private val neighbourhood: Int = 40
) {
    fun build(
        target: Word,
        direction: Direction,
        pool: List<Word>,
        optionCount: Int = 4
    ): Question {
        require(optionCount >= 2) { "hacen falta al menos 2 opciones" }
        val distractors = pickDistractors(target, pool, optionCount - 1)
        val correct = answerOf(target, direction)

        val options = (distractors.map { answerOf(it, direction) } + correct).shuffled(random)
        return Question(
            word = target,
            direction = direction,
            prompt = if (direction == Direction.EN_TO_ES) target.en else target.es,
            hint = target.hint?.takeIf { it.isNotBlank() && it != "-" },
            options = options,
            correctIndex = options.indexOf(correct)
        )
    }

    private fun answerOf(word: Word, direction: Direction): String =
        if (direction == Direction.EN_TO_ES) word.es else word.en

    /**
     * Descarta cualquier palabra que pudiera ser también correcta y ordena el
     * resto por parecido con la respuesta buena.
     */
    internal fun pickDistractors(target: Word, pool: List<Word>, count: Int): List<Word> {
        if (count <= 0) return emptyList()

        val forbiddenEs = target.allEs.map { it.normalized() }.toSet()
        val candidates = pool.filter { candidate ->
            candidate.rank != target.rank &&
                candidate.en.normalized() != target.en.normalized() &&
                candidate.lemma.normalized() != target.lemma.normalized() &&
                candidate.allEs.none { it.normalized() in forbiddenEs }
        }
        if (candidates.isEmpty()) return emptyList()

        // Primero se intenta con la misma categoría gramatical. Si esa bolsa no
        // da para llenar las opciones, se afloja el filtro en vez de repetir.
        val sameKind = candidates.filter { it.pos == target.pos }
        val chosen = drawNear(target, sameKind, count)
        if (chosen.size == count) return chosen

        val taken = chosen.map { it.rank }.toSet()
        return chosen + drawNear(target, candidates.filter { it.rank !in taken }, count - chosen.size)
    }

    /**
     * Coge los [count] finales de entre los vecinos más próximos en frecuencia,
     * sorteando dentro de esa ventana para que no salgan siempre los mismos.
     */
    private fun drawNear(target: Word, from: List<Word>, count: Int): List<Word> {
        if (from.isEmpty() || count <= 0) return emptyList()
        return from.sortedBy { abs(it.rank - target.rank) }
            .take(maxOf(neighbourhood, count))
            .shuffled(random)
            .take(count)
    }
}

/** Compara traducciones ignorando mayúsculas y espacios sobrantes. */
internal fun String.normalized(): String = trim().lowercase()
