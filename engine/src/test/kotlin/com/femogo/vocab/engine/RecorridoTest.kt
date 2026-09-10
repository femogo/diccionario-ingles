package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Simula partidas largas para comprobar cómo avanza el jugador por el
 * diccionario. Los tamaños por nivel son los del diccionario real.
 */
class RecorridoTest {

    private val leitner = Leitner()
    private val scheduler = Scheduler(leitner)
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

    /**
     * Juega [respuestas] preguntas acertando [acierto] de cada uno, avanzando el
     * reloj un poco en cada una. Devuelve las tarjetas resultantes.
     */
    private fun jugar(
        catalogo: List<Word>,
        respuestas: Int,
        acierto: Double,
        segundosPorPregunta: Long = 8
    ): Map<Int, Card> {
        val cards = HashMap<Int, Card>()
        val azar = Random(7)
        var ahora = 1_700_000_000_000L
        var cola = ArrayDeque<Word>()

        repeat(respuestas) {
            if (cola.size <= 8) {
                val pendientes = cola.map { it.rank }.toSet()
                scheduler.buildQueue(catalogo, cards, ahora, size = 40)
                    .filter { it.rank !in pendientes }
                    .forEach { cola.addLast(it) }
            }
            val word = cola.removeFirst()
            val card = cards[word.rank] ?: leitner.newCard(word.rank, ahora)
            cards[word.rank] = leitner.answer(card, azar.nextDouble() < acierto, ahora)
            ahora += segundosPorPregunta * 1000
        }
        return cards
    }

    @Test
    fun `no espera a dominar un nivel para empezar el siguiente`() {
        val catalogo = diccionario()
        val cards = jugar(catalogo, respuestas = 600, acierto = 0.8)

        val vistas = catalogo.filter { cards[it.rank]?.isNew == false }
        val nivelesTocados = vistas.map { it.cefr }.toSet()
        val a1Dominadas = vistas.count { it.cefr == Cefr.A1 && leitner.isMastered(cards[it.rank]!!) }

        println("--- 600 respuestas, 80 % de acierto ---")
        println("palabras distintas vistas: ${vistas.size}")
        println("niveles tocados: ${nivelesTocados.sortedBy { it.ordinal }}")
        println("de A1 en la última caja: $a1Dominadas de 361")
        porNivel.forEach { (cefr, total) ->
            val n = vistas.count { it.cefr == cefr }
            println("  $cefr: $n de $total vistas")
        }

        assertTrue(
            Cefr.A2 in nivelesTocados,
            "con 600 respuestas ya se ha salido de A1 sin dominarlo"
        )
        assertTrue(
            a1Dominadas < 361,
            "y eso ocurre mucho antes de tener A1 entero en la última caja"
        )
    }

    @Test
    fun `lo fallado vuelve pronto y lo acertado se aparta`() {
        val catalogo = diccionario()
        val cards = jugar(catalogo, respuestas = 400, acierto = 0.7)
        val repasos = cards.values.filter { it.seen > 1 }

        println("--- reparto por caja tras 400 respuestas al 70 % ---")
        (1..leitner.boxCount).forEach { caja ->
            println("  caja $caja: ${cards.values.count { it.box == caja }}")
        }
        println("palabras vistas más de una vez: ${repasos.size}")

        assertTrue(repasos.isNotEmpty(), "las falladas tienen que volver dentro de la sesión")
    }

    /**
     * Varias sesiones repartidas en dias. Es el uso real: dentro de una misma
     * tarde ninguna palabra sale de la caja 2, porque el salto a la 3 es de un
     * dia entero.
     */
    private fun jugarDias(
        catalogo: List<Word>,
        dias: Int,
        respuestasPorDia: Int,
        acierto: Double
    ): Map<Int, Card> {
        val cards = HashMap<Int, Card>()
        val azar = Random(11)
        var ahora = 1_700_000_000_000L

        repeat(dias) {
            val cola = ArrayDeque<Word>()
            repeat(respuestasPorDia) {
                if (cola.size <= 8) {
                    val pendientes = cola.map { it.rank }.toSet()
                    scheduler.buildQueue(catalogo, cards, ahora, size = 40)
                        .filter { it.rank !in pendientes }
                        .forEach { cola.addLast(it) }
                }
                val word = cola.removeFirst()
                val card = cards[word.rank] ?: leitner.newCard(word.rank, ahora)
                cards[word.rank] = leitner.answer(card, azar.nextDouble() < acierto, ahora)
                ahora += 8_000L
            }
            // Hasta la sesion del dia siguiente.
            ahora += 24 * 3_600_000L - respuestasPorDia * 8_000L
        }
        return cards
    }

    @Test
    fun `a lo largo de semanas las cajas altas se llenan`() {
        val catalogo = diccionario()
        println("--- 60 respuestas al dia, 80 % de acierto ---")
        listOf(1, 7, 30, 90).forEach { dias ->
            val cards = jugarDias(catalogo, dias, respuestasPorDia = 60, acierto = 0.8)
            val p = progreso.porNivel(catalogo, cards)
            val a1 = p.first { it.cefr == Cefr.A1 }
            val cajas = (1..leitner.boxCount).joinToString(" ") { c ->
                "c$c=" + cards.values.count { it.box == c }
            }
            println("dia $dias: vistas ${cards.size}, A1 al ${(a1.dominio * 100).toInt()} %, " +
                "nivel ${progreso.nivelAlcanzado(p)}  [$cajas]")
        }
        val tresMeses = jugarDias(catalogo, 90, 60, 0.8)
        assertTrue(
            tresMeses.values.any { it.box >= 5 },
            "en tres meses tiene que haber palabras en las cajas altas"
        )
    }

    @Test
    fun `la barra de nivel avanza desde las primeras respuestas`() {
        val catalogo = diccionario()
        listOf(50, 200, 600, 2000).forEach { n ->
            val cards = jugar(catalogo, respuestas = n, acierto = 0.8)
            val p = progreso.porNivel(catalogo, cards)
            val a1 = p.first { it.cefr == Cefr.A1 }
            println("tras $n respuestas -> A1 al ${(a1.dominio * 100).toInt()} %, " +
                "nivel mostrado ${progreso.nivelAlcanzado(p)}")
        }
        val cards = jugar(catalogo, respuestas = 200, acierto = 0.8)
        val a1 = progreso.porNivel(catalogo, cards).first { it.cefr == Cefr.A1 }
        assertTrue(a1.dominio > 0f, "la barra no puede quedarse clavada en cero")
    }
}
