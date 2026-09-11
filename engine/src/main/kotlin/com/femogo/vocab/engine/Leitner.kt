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
    /** Preguntas hasta la siguiente revisión, por caja. Índice 0 = caja 1. */
    private val intervalos: IntArray = INTERVALOS
) {
    init {
        require(intervalos.isNotEmpty()) { "hacen falta intervalos" }
    }

    val boxCount: Int get() = intervalos.size

    /** Una palabra está dominada cuando alcanza la última caja. */
    fun isMastered(card: Card): Boolean = card.box >= boxCount

    /**
     * Aplica el resultado de una respuesta y devuelve la tarjeta actualizada.
     * [turno] es el número de preguntas respondidas hasta ahora.
     */
    fun answer(card: Card, correct: Boolean, turno: Int): Card {
        val box = if (correct) minOf(card.box + 1, boxCount) else 1
        return card.copy(
            box = box,
            dueTurn = turno + intervalos[box - 1],
            seen = card.seen + 1,
            correct = card.correct + if (correct) 1 else 0,
            streak = if (correct) card.streak + 1 else 0
        )
    }

    /** Tarjeta recién introducida, disponible de inmediato. */
    fun newCard(rank: Int): Card = Card(rank = rank, box = 1, dueTurn = 0)

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
        /**
         * Espaciado en preguntas, triplicando en cada caja.
         *
         * Medido en turnos y no en tiempo a propósito: quien juega tres horas
         * seguidas y quien juega diez minutos al día recorren la misma escala,
         * cada uno a su paso. La escala se estira sola con el uso, en vez de
         * castigar al que juega mucho con repasos que aún no ha ganado.
         *
         * Cuatro cajas y no seis, y eso salió de medirlo contra un jugador
         * simulado que aprende y olvida. Con menos cajas se ven muchas más
         * palabras distintas —2622 frente a 2142 en veinte mil preguntas— y esa
         * amplitud compensa de sobra tener menos repasos por palabra: se acaban
         * recordando más, no menos.
         *
         * Tres cajas recordaban todavía algo más, pero se quedan fuera por dos
         * motivos que también están medidos: las palabras sabidas con soltura
         * bajan de 2567 a 2405, y el acierto medio cae del 67 % al 62 %, que es
         * jugar fallando cuatro de cada diez.
         */
        val INTERVALOS = intArrayOf(12, 95, 750, 6000)
    }
}

enum class DirectionMode { EN_TO_ES, ES_TO_EN, PROGRESSIVE }
