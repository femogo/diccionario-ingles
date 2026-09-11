package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Partidas largas contra un diccionario del tamaño del real, para comprobar cómo
 * avanza el jugador. Todo se mide en preguntas respondidas: el motor no sabe qué
 * día es, así que tres horas seguidas y tres semanas a ratos son lo mismo.
 */
class RecorridoTest {

    private val leitner = Leitner()
    private val progreso = ProgresoNivel(leitner)

    private val porNivel = listOf(
        Cefr.A1 to 361, Cefr.A2 to 780, Cefr.B1 to 936,
        Cefr.B2 to 1520, Cefr.C1 to 1717, Cefr.C2 to 1466
    )

    private fun diccionario(): List<Word> {
        var rank = 0
        return porNivel.flatMap { (cefr, cuantas) ->
            (1..cuantas).map { word(rank = ++rank, cefr = cefr) }
        }
    }

    private class Partida(val cards: Map<Int, Card>, val repasos: Int, val primerRepaso: Int)

    private fun jugar(catalogo: List<Word>, preguntas: Int, acierto: Double, semilla: Int = 7): Partida {
        val cola = ColaDePreguntas(Scheduler(leitner), random = Random(semilla))
        val cards = HashMap<Int, Card>()
        val azar = Random(semilla)
        var repasos = 0
        var primerRepaso = -1

        repeat(preguntas) { turno ->
            val w = cola.siguiente(catalogo, cards, turno, null) ?: return@repeat
            val previa = cards[w.rank]
            if (previa != null && !previa.isNew) {
                repasos++
                if (primerRepaso < 0) primerRepaso = turno
            }
            val acierta = azar.nextDouble() < acierto
            cards[w.rank] = leitner.answer(previa ?: leitner.newCard(w.rank), acierta, turno)
            if (!acierta) cola.reintentar(w)
        }
        return Partida(cards, repasos, primerRepaso)
    }

    @Test
    fun `no espera a dominar un nivel para empezar el siguiente`() {
        val catalogo = diccionario()
        val p = jugar(catalogo, preguntas = 600, acierto = 0.8)
        val vistas = catalogo.filter { p.cards[it.rank]?.isNew == false }
        val nivelesTocados = vistas.map { it.cefr }.toSet()
        val a1Dominadas = vistas.count { it.cefr == Cefr.A1 && leitner.isMastered(p.cards[it.rank]!!) }

        println("--- 600 preguntas al 80 % ---")
        println("palabras distintas vistas: ${vistas.size}")
        println("niveles tocados: ${nivelesTocados.sortedBy { it.ordinal }}")
        println("de A1 en la última caja: $a1Dominadas de 361")

        assertTrue(Cefr.A2 in nivelesTocados, "se sale de A1 sin haberlo dominado")
        assertTrue(a1Dominadas < 361, "y mucho antes de tenerlo entero en la última caja")
    }

    @Test
    fun `lo fallado vuelve dentro de la misma tanda`() {
        val p = jugar(diccionario(), preguntas = 300, acierto = 0.7)

        println("--- 300 preguntas al 70 % ---")
        println("repasos: ${p.repasos}, primero en la pregunta ${p.primerRepaso}")

        assertTrue(p.primerRepaso in 0..30, "el primer repaso llegó en la ${p.primerRepaso}")
        assertTrue(p.repasos > 50, "solo ${p.repasos} repasos de 300")
    }

    @Test
    fun `jugando mucho las cajas altas se llenan`() {
        val catalogo = diccionario()
        println("--- avance por preguntas respondidas ---")
        listOf(300, 1000, 5000, 20000).forEach { n ->
            val p = jugar(catalogo, n, acierto = 0.8)
            val a1 = progreso.porNivel(catalogo, p.cards).first { it.cefr == Cefr.A1 }
            val cajas = (1..leitner.boxCount).joinToString(" ") { c ->
                "c$c=" + p.cards.values.count { it.box == c }
            }
            println("$n preguntas: vistas ${p.cards.size}, A1 al ${(a1.dominio * 100).toInt()} %, " +
                "nivel ${progreso.nivelAlcanzado(progreso.porNivel(catalogo, p.cards))}  [$cajas]")
        }

        val larga = jugar(catalogo, 20000, acierto = 0.8)
        assertTrue(
            larga.cards.values.any { it.box >= 5 },
            "jugando mucho tiene que haber palabras en las cajas altas"
        )
    }
}
