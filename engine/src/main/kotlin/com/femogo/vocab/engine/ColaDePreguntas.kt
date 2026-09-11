package com.femogo.vocab.engine

import kotlin.random.Random

/**
 * La cola de preguntas en curso.
 *
 * Se rellena sola antes de agotarse, así que el juego no tiene tandas ni final.
 *
 * Su otro trabajo es el reintento inmediato, y es el que justifica que esto sea
 * una clase aparte. El espaciado de [Leitner] cuenta en minutos y días, lo que
 * da por supuesto un ritmo de estudio pausado. Jugando a tres segundos por
 * palabra, los diez minutos de la primera caja son doscientas preguntas de por
 * medio: medido en simulación, de sesenta palabras falladas solo diecisiete
 * volvían a aparecer, y la primera en la pregunta doscientos treinta y dos.
 *
 * Por eso el primer reintento se mide en preguntas y no en tiempo. Lo fallado
 * vuelve una docena de preguntas después, se juegue rápido o despacio. El
 * vencimiento por reloj sigue ahí como red para cuando se cierra la aplicación.
 */
class ColaDePreguntas(
    private val scheduler: Scheduler,
    private val tamaño: Int = 40,
    private val rellenarBajo: Int = 8,
    /** A cuántas preguntas vista vuelve lo que se acaba de fallar. */
    private val distanciaReintento: IntRange = 8..16,
    private val random: Random = Random.Default
) {
    private val cola = ArrayDeque<Word>()

    val pendientes: Int get() = cola.size

    fun siguiente(
        catalog: List<Word>,
        cards: Map<Int, Card>,
        now: Long,
        aciertoReciente: Float?
    ): Word? {
        if (cola.size <= rellenarBajo) rellenar(catalog, cards, now, aciertoReciente)
        return cola.removeFirstOrNull()
    }

    /**
     * Devuelve una palabra a la cola, unas preguntas más adelante. Ni la
     * siguiente, que sería contestar de memoria, ni tan lejos que se olvide.
     */
    fun reintentar(word: Word) {
        if (cola.any { it.rank == word.rank }) return
        val distancia = random.nextInt(distanciaReintento.first, distanciaReintento.last + 1)
        cola.add(minOf(distancia, cola.size), word)
    }

    fun vaciar() = cola.clear()

    private fun rellenar(
        catalog: List<Word>,
        cards: Map<Int, Card>,
        now: Long,
        aciertoReciente: Float?
    ) {
        val yaEnCola = cola.map { it.rank }.toSet()
        scheduler.buildQueue(catalog, cards, now, tamaño, aciertoReciente)
            .filter { it.rank !in yaEnCola }
            .forEach { cola.addLast(it) }
    }
}
