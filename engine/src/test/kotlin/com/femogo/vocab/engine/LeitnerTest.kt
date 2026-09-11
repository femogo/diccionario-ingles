package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeitnerTest {
    private val leitner = Leitner()

    @Test
    fun `acertar sube de caja y aleja la revision`() {
        val card = leitner.newCard(rank = 1)
        val after = leitner.answer(card, correct = true, turno = 100)

        assertEquals(2, after.box)
        assertEquals(100 + 50, after.dueTurn, "caja 2: cincuenta preguntas más adelante")
        assertEquals(1, after.seen)
        assertEquals(1, after.correct)
        assertEquals(1, after.streak)
    }

    @Test
    fun `fallar devuelve a la primera caja y rompe la racha`() {
        var card = leitner.newCard(1)
        repeat(3) { card = leitner.answer(card, correct = true, turno = 0) }
        assertEquals(4, card.box)

        val failed = leitner.answer(card, correct = false, turno = 500)
        assertEquals(1, failed.box)
        assertEquals(0, failed.streak)
        assertEquals(512, failed.dueTurn)
        assertEquals(3, failed.correct, "los aciertos previos no se borran")
    }

    @Test
    fun `la caja no pasa del maximo`() {
        var card = leitner.newCard(1)
        repeat(20) { card = leitner.answer(card, correct = true, turno = 0) }

        assertEquals(leitner.boxCount, card.box)
        assertTrue(leitner.isMastered(card))
        assertEquals(20, card.streak)
    }

    @Test
    fun `una palabra dominada se espacia mas que una recien fallada`() {
        var mastered = leitner.newCard(1)
        repeat(6) { mastered = leitner.answer(mastered, correct = true, turno = 1000) }
        val struggling = leitner.answer(leitner.newCard(2), correct = false, turno = 1000)

        assertTrue(
            mastered.dueTurn > struggling.dueTurn,
            "lo dominado debe repetirse menos, no más"
        )
    }

    @Test
    fun `el espaciado no depende del reloj`() {
        // Una tarde de tres horas y una semana sin abrir la aplicación son lo
        // mismo: lo único que cuenta son las preguntas respondidas.
        val a = leitner.answer(leitner.newCard(1), correct = true, turno = 40)
        val b = leitner.answer(leitner.newCard(1), correct = true, turno = 40)

        assertEquals(a.dueTurn, b.dueTurn)
    }

    @Test
    fun `el modo progresivo invierte la direccion al asentarse`() {
        val fresh = Card(rank = 1, box = 1)
        val settled = Card(rank = 1, box = 4)

        assertEquals(Direction.EN_TO_ES, leitner.directionFor(fresh, DirectionMode.PROGRESSIVE))
        assertEquals(Direction.ES_TO_EN, leitner.directionFor(settled, DirectionMode.PROGRESSIVE))
        assertEquals(Direction.EN_TO_ES, leitner.directionFor(settled, DirectionMode.EN_TO_ES))
    }

    @Test
    fun `una tarjeta nueva esta disponible de inmediato`() {
        val card = leitner.newCard(7)
        assertTrue(card.isNew)
        assertFalse(leitner.isMastered(card))
        assertEquals(0, card.dueTurn)
    }
}
