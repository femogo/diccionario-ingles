package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeitnerTest {
    private val leitner = Leitner()
    private val now = 1_700_000_000_000L
    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun `acertar sube de caja y aleja la revision`() {
        val card = leitner.newCard(rank = 1, now = now)
        val after = leitner.answer(card, correct = true, now = now)

        assertEquals(2, after.box)
        assertEquals(now + minutes(1_440), after.dueAt)
        assertEquals(1, after.seen)
        assertEquals(1, after.correct)
        assertEquals(1, after.streak)
    }

    @Test
    fun `fallar devuelve a la primera caja y rompe la racha`() {
        var card = leitner.newCard(1, now)
        repeat(3) { card = leitner.answer(card, correct = true, now = now) }
        assertEquals(4, card.box)

        val failed = leitner.answer(card, correct = false, now = now)
        assertEquals(1, failed.box)
        assertEquals(0, failed.streak)
        assertEquals(now + minutes(10), failed.dueAt)
        assertEquals(3, failed.correct, "los aciertos previos no se borran")
    }

    @Test
    fun `la caja no pasa del maximo`() {
        var card = leitner.newCard(1, now)
        repeat(20) { card = leitner.answer(card, correct = true, now = now) }

        assertEquals(leitner.boxCount, card.box)
        assertTrue(leitner.isMastered(card))
        assertEquals(20, card.streak)
    }

    @Test
    fun `una palabra dominada se espacia mas que una recien fallada`() {
        var mastered = leitner.newCard(1, now)
        repeat(6) { mastered = leitner.answer(mastered, correct = true, now = now) }
        val struggling = leitner.answer(leitner.newCard(2, now), correct = false, now = now)

        assertTrue(
            mastered.dueAt > struggling.dueAt,
            "lo dominado debe repetirse menos, no más"
        )
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
        val card = leitner.newCard(7, now)
        assertTrue(card.isNew)
        assertFalse(leitner.isMastered(card))
        assertEquals(now, card.dueAt)
    }
}
