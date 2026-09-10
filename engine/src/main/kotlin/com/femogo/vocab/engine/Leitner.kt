package com.femogo.vocab.engine

/**
 * Repetición espaciada por cajas (Leitner).
 *
 * Cada palabra vive en una caja del 1 al 6. Acertar la sube una caja y retrasa
 * la siguiente revisión; fallar la devuelve a la caja 1. Es deliberadamente lo
 * contrario de "cuanto más la sabes, más sale": lo dominado se espacia para
 * dejar sitio a lo que todavía falla.
 *
 * Toda la configuración vive aquí. Cambiar de algoritmo (SM-2, FSRS) significa
 * sustituir esta clase sin tocar el resto de la aplicación.
 */
class Leitner(
    /** Minutos hasta la siguiente revisión, por caja. Índice 0 = caja 1. */
    private val intervalMinutes: LongArray = DEFAULT_INTERVALS
) {
    init {
        require(intervalMinutes.isNotEmpty()) { "hacen falta intervalos" }
    }

    val boxCount: Int get() = intervalMinutes.size

    /** Una palabra está dominada cuando alcanza la última caja. */
    fun isMastered(card: Card): Boolean = card.box >= boxCount

    /**
     * Aplica el resultado de una respuesta y devuelve la tarjeta actualizada.
     * [now] es epoch en milisegundos; se pasa como parámetro para que el
     * comportamiento sea comprobable sin depender del reloj real.
     */
    fun answer(card: Card, correct: Boolean, now: Long): Card {
        val box = if (correct) minOf(card.box + 1, boxCount) else 1
        return card.copy(
            box = box,
            dueAt = now + intervalMinutes[box - 1] * 60_000L,
            seen = card.seen + 1,
            correct = card.correct + if (correct) 1 else 0,
            streak = if (correct) card.streak + 1 else 0,
            lastSeenAt = now
        )
    }

    /** Tarjeta recién introducida, disponible de inmediato. */
    fun newCard(rank: Int, now: Long): Card = Card(rank = rank, box = 1, dueAt = now)

    /**
     * Cómo se pregunta una palabra según lo asentada que esté.
     *
     * Las dos primeras cajas preguntan inglés -> español, que es reconocer.
     * A partir de la tercera se invierte a español -> inglés, que es producir y
     * cuesta bastante más. Así la dificultad sube con el dominio en vez de
     * quedarse plana.
     */
    fun directionFor(card: Card, mode: DirectionMode): Direction = when (mode) {
        DirectionMode.EN_TO_ES -> Direction.EN_TO_ES
        DirectionMode.ES_TO_EN -> Direction.ES_TO_EN
        DirectionMode.PROGRESSIVE -> if (card.box <= 2) Direction.EN_TO_ES else Direction.ES_TO_EN
    }

    companion object {
        /** 10 min, 1 día, 3 días, 1 semana, 3 semanas, 2 meses. */
        val DEFAULT_INTERVALS = longArrayOf(10, 1_440, 4_320, 10_080, 30_240, 86_400)
    }
}

enum class DirectionMode { EN_TO_ES, ES_TO_EN, PROGRESSIVE }
