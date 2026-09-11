package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Partidas muy largas, para ver si el juego se sostiene: que las palabras
 * dominadas dejen sitio a nuevas, y que ni se estanque ni se desborde.
 */
class FlujoTest {
    private val leitner = Leitner()

    private fun catalogo(): List<Word> {
        val porNivel = listOf(
            Cefr.A1 to 361, Cefr.A2 to 780, Cefr.B1 to 936,
            Cefr.B2 to 1520, Cefr.C1 to 1717, Cefr.C2 to 1466)
        var rank = 0
        return porNivel.flatMap { (c, n) -> (1..n).map { word(rank = ++rank, cefr = c) } }
    }

    private class Resultado(
        val vistas: Int,
        val dominadas: Int,
        val circulando: Int,
        val atrasoMaximo: Int,
        val nuevasPorTramo: List<Int>
    )

    private fun partida(preguntas: Int, mezcla: Mezcla = Mezcla()): Resultado {
        val catalogo = catalogo()
        val cola = ColaDePreguntas(Scheduler(leitner, mezcla), random = Random(5))
        val cards = HashMap<Int, Card>()
        val azar = Random(5)
        val tramo = 2000
        var nuevasTramo = 0
        val nuevasPorTramo = ArrayList<Int>()
        var atrasoMaximo = 0

        repeat(preguntas) { turno ->
            val w = cola.siguiente(catalogo, cards, turno, null) ?: return@repeat
            val previa = cards[w.rank]
            if (previa == null || previa.isNew) nuevasTramo++
            val ok = azar.nextDouble() < 0.8
            cards[w.rank] = leitner.answer(previa ?: leitner.newCard(w.rank), ok, turno)
            if (!ok) cola.reintentar(w)

            if (turno % 500 == 0) {
                atrasoMaximo = maxOf(atrasoMaximo, cards.values.count { !it.isNew && it.dueTurn <= turno })
            }
            if ((turno + 1) % tramo == 0) {
                nuevasPorTramo.add(nuevasTramo)
                nuevasTramo = 0
            }
        }
        val dominadas = cards.values.count { leitner.isMastered(it) }
        return Resultado(cards.size, dominadas, cards.size - dominadas, atrasoMaximo, nuevasPorTramo)
    }

    @Test
    fun `dominar palabras deja sitio a palabras nuevas`() {
        val r = partida(20000)
        println("20000 preguntas: vistas ${r.vistas}, dominadas ${r.dominadas}, " +
            "en circulación ${r.circulando}, atraso máximo ${r.atrasoMaximo}")
        println("nuevas por cada 2000 preguntas: ${r.nuevasPorTramo}")

        assertTrue(r.dominadas > r.circulando, "la mayoría de lo visto debería acabar dominado")
        assertTrue(r.vistas > 1500, "solo ${r.vistas} palabras vistas en 20000 preguntas")
    }

    @Test
    fun `la carga de repasos se estabiliza en vez de dispararse`() {
        // Con demasiadas novedades la deuda crece sin límite y nada llega nunca
        // a la última caja: el diccionario se recorre sin aprender nada.
        val r = partida(20000)
        assertTrue(
            r.circulando < 900,
            "${r.circulando} palabras a medias a la vez es una carga que no se paga"
        )
    }

    @Test
    fun `un minimo de novedades alto hunde el aprendizaje`() {
        val sano = partida(20000)
        val excesivo = partida(20000, Mezcla(minimoNovedad = 0.25f))

        println("sano: ${sano.vistas} vistas / ${sano.dominadas} dominadas")
        println("excesivo: ${excesivo.vistas} vistas / ${excesivo.dominadas} dominadas")

        assertTrue(excesivo.vistas > sano.vistas, "ver más palabras es lo fácil")
        assertTrue(
            excesivo.dominadas < sano.dominadas / 2,
            "y sale carísimo: ${excesivo.dominadas} dominadas frente a ${sano.dominadas}"
        )
    }
}
