package com.femogo.vocab.engine

import kotlin.random.Random

/**
 * La cola de preguntas en curso.
 *
 * Se rellena sola antes de agotarse, así que el juego no tiene tandas ni final.
 *
 * El reintento devuelve lo recién fallado a la propia cola, unas preguntas más
 * adelante. Es más corto que el intervalo de la primera caja y sirve para que
 * fallar tenga consecuencia inmediata sin repetir la palabra a continuación,
 * que sería contestar de memoria.
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
        turno: Int,
        aciertoReciente: Float?
    ): Word? {
        if (cola.size <= rellenarBajo) rellenar(catalog, cards, turno, aciertoReciente)
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
        turno: Int,
        aciertoReciente: Float?
    ) {
        val yaEnCola = cola.map { it.rank }.toSet()
        scheduler.buildQueue(catalog, cards, turno, tamaño, aciertoReciente)
            .filter { it.rank !in yaEnCola }
            .forEach { cola.addLast(it) }
    }
}
