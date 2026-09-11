package com.femogo.vocab.engine

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compara escalas con distinto número de cajas contra un jugador que olvida.
 *
 * Todas las escalas empiezan y terminan en el mismo intervalo; lo único que
 * cambia es en cuántos pasos se recorre. Así la comparación mide el número de
 * cajas y no de paso el espaciado total.
 */
class CuantasCajasTest {

    private fun escalaDe(cajas: Int, primero: Int = 12, ultimo: Int = 6000): IntArray {
        if (cajas == 1) return intArrayOf(primero)
        val razon = (ultimo.toDouble() / primero).pow(1.0 / (cajas - 1))
        return IntArray(cajas) { (primero * razon.pow(it)).toInt() }
    }

    private class Balance(
        val cajas: Int,
        val vistas: Int,
        val enUltimaCaja: Int,
        val recordadas: Double,
        val solidas: Int,
        val aciertoMedio: Double,
        val falsoDominio: Double
    )

    private fun jugar(cajas: Int, preguntas: Int, opciones: Int = 4): Balance {
        val escala = escalaDe(cajas)
        val leitner = Leitner(escala)
        val catalogo = run {
            val porNivel = listOf(
                Cefr.A1 to 361, Cefr.A2 to 780, Cefr.B1 to 936,
                Cefr.B2 to 1520, Cefr.C1 to 1717, Cefr.C2 to 1466)
            var rank = 0
            porNivel.flatMap { (c, n) -> (1..n).map { word(rank = ++rank, cefr = c) } }
        }
        val cola = ColaDePreguntas(Scheduler(leitner), random = Random(5))
        val aprendiz = Aprendiz(Random(5))
        val cards = HashMap<Int, Card>()
        var aciertos = 0
        var respondidas = 0

        repeat(preguntas) { turno ->
            val w = cola.siguiente(catalogo, cards, turno, null) ?: return@repeat
            val acierta = aprendiz.responde(w.rank, turno, opciones)
            aprendiz.registra(w.rank, turno, acierta)
            cards[w.rank] = leitner.answer(cards[w.rank] ?: leitner.newCard(w.rank), acierta, turno)
            if (!acierta) cola.reintentar(w)
            aciertos += if (acierta) 1 else 0
            respondidas++
        }

        val final = preguntas
        val enUltima = cards.values.filter { leitner.isMastered(it) }
        // De las que el motor da por dominadas, ¿cuántas recuerda de verdad?
        val recuerdoDeLasDominadas = enUltima.sumOf { aprendiz.retencion(it.rank, final) }
        return Balance(
            cajas = cajas,
            vistas = cards.size,
            enUltimaCaja = enUltima.size,
            recordadas = aprendiz.palabrasRecordadas(final),
            solidas = aprendiz.palabrasSolidas(final),
            aciertoMedio = aciertos.toDouble() / respondidas,
            falsoDominio = if (enUltima.isEmpty()) 0.0
            else 1 - recuerdoDeLasDominadas / enUltima.size
        )
    }

    @Test
    fun `cuantas cajas compensan`() {
        println("cajas  escala                        vistas  ultima  recordadas  solidas  acierto  falso dominio")
        listOf(3, 4, 5, 6, 8).forEach { n ->
            val b = jugar(n, 20000)
            println("%5d  %-28s %6d %7d %11.0f %8d %7.0f%% %12.0f%%".format(
                n, escalaDe(n).joinToString(" "), b.vistas, b.enUltimaCaja,
                b.recordadas, b.solidas, b.aciertoMedio * 100, b.falsoDominio * 100))
        }
    }

    @Test
    fun `menos cajas no significa aprender mas`() {
        val tres = jugar(3, 20000)
        val seis = jugar(6, 20000)

        assertTrue(
            tres.enUltimaCaja > seis.enUltimaCaja,
            "con tres cajas se llega antes a la última: es lo que se busca"
        )
        assertTrue(
            tres.falsoDominio > seis.falsoDominio,
            "pero esa etiqueta vale menos: falso dominio ${tres.falsoDominio} frente a ${seis.falsoDominio}"
        )
    }
}
