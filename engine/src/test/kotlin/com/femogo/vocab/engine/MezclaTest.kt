package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MezclaTest {
    private val mezcla = Mezcla()
    private val leitner = Leitner()

    private fun progresos(vararg dominios: Pair<Cefr, Float>): List<NivelProgreso> {
        val mapa = dominios.toMap()
        return Cefr.entries.map { NivelProgreso(it, total = 100, vistas = 0, dominio = mapa[it] ?: 0f) }
    }

    private val hayDeTodo = Cefr.entries.associateWith { 500 }

    @Test
    fun `reserva palabras nuevas aunque la sesion este llena de repasos`() {
        // Es el caso que rompía el juego a los tres meses: 40 repasos vencidos
        // dejaban cero novedades y el jugador dejaba de descubrir palabras.
        assertTrue(mezcla.cuantasNuevas(total = 40, vencidas = 40) >= 5)
    }

    @Test
    fun `la reserva cede cuando la deuda de repasos se dispara`() {
        // Sin esto entran novedades mas deprisa de lo que se pueden repasar y
        // nada llega nunca a las cajas altas.
        val holgado = mezcla.cuantasNuevas(total = 40, vencidas = 30)
        val atascado = mezcla.cuantasNuevas(total = 40, vencidas = 400)

        assertTrue(atascado < holgado, "con 400 pendientes toca repasar, no descubrir")
        assertTrue(atascado >= 2, "pero nunca se deja de ver algo nuevo")
    }

    @Test
    fun `con pocos repasos llena el resto con novedades`() {
        assertEquals(35, mezcla.cuantasNuevas(total = 40, vencidas = 5))
    }

    @Test
    fun `sin nada pendiente son todas nuevas`() {
        assertEquals(40, mezcla.cuantasNuevas(total = 40, vencidas = 0))
    }

    @Test
    fun `al empezar casi todo sale del primer nivel`() {
        val reparto = mezcla.repartir(progresos(), hayDeTodo, cuantas = 20, aciertoReciente = null)
        val a1 = reparto[Cefr.A1] ?: 0

        assertTrue(a1 >= 9, "A1 debería llevarse cerca de la mitad, se llevó $a1")
        assertTrue((reparto[Cefr.C2] ?: 0) <= 1, "C2 casi no debería aparecer el primer día")
    }

    @Test
    fun `dominar un nivel cede su sitio a los siguientes`() {
        val principio = mezcla.repartir(progresos(), hayDeTodo, 20, null)
        val despues = mezcla.repartir(
            progresos(Cefr.A1 to 0.9f, Cefr.A2 to 0.5f), hayDeTodo, 20, null
        )

        assertTrue(
            (despues[Cefr.A1] ?: 0) < (principio[Cefr.A1] ?: 0),
            "A1 dominado tiene que pedir menos palabras nuevas"
        )
        assertTrue(
            (despues[Cefr.B1] ?: 0) > (principio[Cefr.B1] ?: 0),
            "y ese hueco lo recogen los niveles de arriba"
        )
    }

    @Test
    fun `no se abre un nivel cuyo anterior esta en blanco`() {
        val reparto = mezcla.repartir(progresos(), hayDeTodo, cuantas = 20, aciertoReciente = null)
        val arriba = (reparto[Cefr.B2] ?: 0) + (reparto[Cefr.C1] ?: 0) + (reparto[Cefr.C2] ?: 0)

        assertTrue(arriba <= 4, "sin base, lo difícil apenas asoma; asomó $arriba de 20")
    }

    @Test
    fun `acertar mucho mezcla mas material dificil`() {
        val comodo = mezcla.repartir(progresos(Cefr.A1 to 0.4f), hayDeTodo, 40, aciertoReciente = 0.95f)
        val apurado = mezcla.repartir(progresos(Cefr.A1 to 0.4f), hayDeTodo, 40, aciertoReciente = 0.55f)

        assertTrue(
            (comodo[Cefr.A1] ?: 0) < (apurado[Cefr.A1] ?: 0),
            "fallando se concentra abajo, acertando se abre la mano"
        )
    }

    @Test
    fun `reparte exactamente lo pedido`() {
        listOf(1, 7, 20, 40).forEach { cuantas ->
            val reparto = mezcla.repartir(progresos(Cefr.A1 to 0.3f), hayDeTodo, cuantas, null)
            assertEquals(cuantas, reparto.values.sum(), "pedidas $cuantas")
        }
    }

    @Test
    fun `no pide palabras de un nivel que ya se agoto`() {
        val disponibles = mapOf(Cefr.A1 to 3, Cefr.A2 to 100)
        val reparto = mezcla.repartir(progresos(), disponibles, cuantas = 20, aciertoReciente = null)

        assertTrue((reparto[Cefr.A1] ?: 0) <= 3)
        assertTrue(Cefr.B1 !in reparto, "un nivel sin palabras disponibles no entra en el reparto")
        assertEquals(20, reparto.values.sum())
    }

    @Test
    fun `sin diccionario disponible no reparte nada`() {
        assertTrue(mezcla.repartir(progresos(), emptyMap(), 20, null).isEmpty())
    }
}
