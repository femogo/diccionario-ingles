package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Partidas muy largas contra un jugador que aprende y olvida, para ver si el
 * juego se sostiene: que lo dominado deje sitio a palabras nuevas, y que la
 * prisa por ver vocabulario no acabe comiéndose el aprendizaje.
 *
 * La medida que importa es [Aprendiz.palabrasSolidas]: contar palabras en la
 * última caja diría que cualquier atajo funciona, porque la última caja la
 * reparte el propio motor.
 */
class FlujoTest {

    private fun catalogo(): List<Word> {
        val porNivel = listOf(
            Cefr.A1 to 361, Cefr.A2 to 780, Cefr.B1 to 936,
            Cefr.B2 to 1520, Cefr.C1 to 1717, Cefr.C2 to 1466)
        var rank = 0
        return porNivel.flatMap { (c, n) -> (1..n).map { word(rank = ++rank, cefr = c) } }
    }

    private class Resultado(
        val vistas: Int,
        val enUltimaCaja: Int,
        val solidas: Int,
        val circulando: Int,
        val acierto: Double,
        val nuevasPorTramo: List<Int>
    )

    private fun partida(preguntas: Int, mezcla: Mezcla = Mezcla()): Resultado {
        val leitner = Leitner()
        val catalogo = catalogo()
        val cola = ColaDePreguntas(Scheduler(leitner, mezcla), random = Random(5))
        val aprendiz = Aprendiz(Random(5))
        val cards = HashMap<Int, Card>()
        var aciertos = 0
        var nuevasTramo = 0
        val nuevasPorTramo = ArrayList<Int>()

        repeat(preguntas) { turno ->
            val w = cola.siguiente(catalogo, cards, turno, null) ?: return@repeat
            val previa = cards[w.rank]
            if (previa == null || previa.isNew) nuevasTramo++
            val acierta = aprendiz.responde(w.rank, turno, 4)
            aprendiz.registra(w.rank, turno, acierta)
            cards[w.rank] = leitner.answer(previa ?: leitner.newCard(w.rank), acierta, turno)
            if (!acierta) cola.reintentar(w)
            if (acierta) aciertos++
            if ((turno + 1) % 2000 == 0) {
                nuevasPorTramo.add(nuevasTramo)
                nuevasTramo = 0
            }
        }
        val enUltima = cards.values.count { leitner.isMastered(it) }
        return Resultado(
            vistas = cards.size,
            enUltimaCaja = enUltima,
            solidas = aprendiz.palabrasSolidas(preguntas),
            circulando = cards.size - enUltima,
            acierto = aciertos.toDouble() / preguntas,
            nuevasPorTramo = nuevasPorTramo
        )
    }

    @Test
    fun `dominar palabras deja sitio a palabras nuevas`() {
        val r = partida(20000)
        println("20000 preguntas: vistas ${r.vistas}, en la última caja ${r.enUltimaCaja}, " +
            "sabidas de verdad ${r.solidas}, acierto ${(r.acierto * 100).toInt()} %")
        println("nuevas por cada 2000 preguntas: ${r.nuevasPorTramo}")

        assertTrue(r.enUltimaCaja > r.circulando, "la mayoría de lo visto debería asentarse")
        assertTrue(r.vistas > 1500, "solo ${r.vistas} palabras vistas en 20000 preguntas")
    }

    @Test
    fun `el minimo de novedades esta donde mas se aprende`() {
        // Contar palabras en la última caja premiaría cualquier atajo, porque
        // esa etiqueta la reparte el motor. Lo que se compara es cuántas
        // palabras sabría el jugador si le preguntaran al final.
        val candidatos = listOf(0.10f, 0.15f, 0.20f, 0.25f, 0.30f)
        val medidas = candidatos.associateWith { partida(20000, Mezcla(minimoNovedad = it)) }

        medidas.forEach { (m, r) ->
            println("mínimo ${(m * 100).toInt()} %: vistas ${r.vistas}, " +
                "sabidas ${r.solidas}, acierto ${(r.acierto * 100).toInt()} %")
        }

        val elegido = medidas[0.15f]!!
        val mejor = medidas.values.maxByOrNull { it.solidas }!!
        assertTrue(
            elegido.solidas >= mejor.solidas * 0.9,
            "el elegido se queda a más de un 10 % del mejor: " +
                "${elegido.solidas} frente a ${mejor.solidas}"
        )

        // Lo que de verdad se está comprando con no coger el máximo: quedarse en
        // una cuesta y no en una cima, para que equivocarse no salga caro.
        val unEscalonMas = medidas[0.20f]!!
        assertTrue(
            unEscalonMas.solidas >= elegido.solidas,
            "el ajuste está en la pendiente de bajada, no en la de subida"
        )
    }
}
