package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A qué ritmo vuelve lo fallado según lo deprisa que se juegue. El espaciado por
 * reloj se hunde con un jugador rápido; el reintento por turnos no.
 */
class RitmoTest {
    private val leitner = Leitner()

    private fun catalogoGrande() = Cefr.entries.flatMapIndexed { i, cefr ->
        (1..1000).map { word(rank = i * 1000 + it, cefr = cefr) }
    }

    private data class Medida(val repasos: Int, val primero: Int, val falladasVistas: Int, val falladas: Int)

    private fun jugar(segundosPorPalabra: Long, preguntas: Int = 300): Medida {
        val catalogo = catalogoGrande()
        val cola = ColaDePreguntas(Scheduler(leitner), random = Random(5))
        val cards = HashMap<Int, Card>()
        val azar = Random(3)
        var ahora = 1_700_000_000_000L
        var repasos = 0
        var primero = -1
        val falladas = HashSet<Int>()
        val revisitadas = HashSet<Int>()

        repeat(preguntas) { n ->
            val w = cola.siguiente(catalogo, cards, ahora, null) ?: return@repeat
            val previa = cards[w.rank]
            if (previa != null && !previa.isNew) {
                repasos++
                if (primero < 0) primero = n
                if (w.rank in falladas) revisitadas.add(w.rank)
            }
            val acierta = azar.nextDouble() < 0.8
            if (!acierta) falladas.add(w.rank)
            cards[w.rank] = leitner.answer(previa ?: leitner.newCard(w.rank, ahora), acierta, ahora)
            if (!acierta) cola.reintentar(w)
            ahora += segundosPorPalabra * 1000
        }
        return Medida(repasos, primero, revisitadas.size, falladas.size)
    }

    @Test
    fun `lo fallado vuelve pronto a cualquier ritmo`() {
        listOf(3L, 8L, 20L).forEach { s ->
            val m = jugar(s)
            println("a $s s/palabra (${300 * s / 60} min): repasos ${m.repasos} de 300, " +
                "primero en la #${m.primero}, falladas revisitadas ${m.falladasVistas} de ${m.falladas}")
        }

        val rapido = jugar(3L)
        assertTrue(rapido.primero in 0..30, "el primer repaso llegó en la #${rapido.primero}")
        assertTrue(
            rapido.falladasVistas > rapido.falladas / 2,
            "solo volvieron ${rapido.falladasVistas} de ${rapido.falladas} falladas"
        )
    }

    @Test
    fun `jugar deprisa o despacio da un reparto parecido`() {
        val rapido = jugar(3L)
        val lento = jugar(20L)
        println("rápido: ${rapido.repasos} repasos | lento: ${lento.repasos} repasos")

        val diferencia = kotlin.math.abs(rapido.repasos - lento.repasos)
        assertTrue(
            diferencia < 40,
            "el ritmo no debería cambiar tanto el juego: $diferencia de diferencia"
        )
    }
}
