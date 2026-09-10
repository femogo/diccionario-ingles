package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulerTest {
    private val leitner = Leitner()
    private val scheduler = Scheduler(leitner, newPerDay = 5)
    private val now = 1_700_000_000_000L
    private val catalog = catalog(100)

    @Test
    fun `las vencidas van antes que las nuevas`() {
        val cards = mapOf(
            80 to Card(rank = 80, box = 2, dueAt = now - 1000, seen = 3),
            90 to Card(rank = 90, box = 3, dueAt = now - 5000, seen = 4)
        )
        val session = scheduler.buildSession(catalog, cards, now, size = 5)

        assertEquals(listOf(80, 90), session.take(2).map { it.rank })
    }

    @Test
    fun `entre vencidas manda la caja mas baja`() {
        val cards = mapOf(
            10 to Card(rank = 10, box = 4, dueAt = now - 9999, seen = 8),
            20 to Card(rank = 20, box = 1, dueAt = now - 10, seen = 2)
        )
        val session = scheduler.buildSession(catalog, cards, now, size = 2)

        assertEquals(20, session.first().rank, "lo que peor se sabe se pregunta antes")
    }

    @Test
    fun `las palabras nuevas entran por orden de frecuencia`() {
        val session = scheduler.buildSession(catalog, emptyMap(), now, size = 4)
        assertEquals(listOf(1, 2, 3, 4), session.map { it.rank })
    }

    @Test
    fun `el tope diario de nuevas se respeta`() {
        val session = scheduler.buildSession(catalog, emptyMap(), now, size = 30, newIntroducedToday = 0)
        assertEquals(5, session.size, "newPerDay = 5")
    }

    @Test
    fun `lo ya introducido hoy descuenta del tope`() {
        val session = scheduler.buildSession(catalog, emptyMap(), now, size = 30, newIntroducedToday = 4)
        assertEquals(1, session.size)
    }

    @Test
    fun `sin nuevas disponibles adelanta revisiones futuras`() {
        // Todo el catálogo visto y programado a futuro: sin adelanto no habría nada que jugar.
        val cards = catalog.associate {
            it.rank to Card(rank = it.rank, box = 2, dueAt = now + 86_400_000L, seen = 1)
        }
        val session = scheduler.buildSession(catalog, cards, now, size = 6, newIntroducedToday = 99)

        assertEquals(6, session.size)
        assertEquals(6, session.map { it.rank }.toSet().size, "sin repetidas")
    }

    @Test
    fun `no adelanta palabras ya dominadas`() {
        val cards = catalog.associate {
            it.rank to Card(rank = it.rank, box = leitner.boxCount, dueAt = now + 1_000_000L, seen = 9)
        }
        val session = scheduler.buildSession(catalog, cards, now, size = 5, newIntroducedToday = 99)
        assertTrue(session.isEmpty())
    }

    @Test
    fun `una palabra no sale dos veces en la misma sesion`() {
        val cards = mapOf(1 to Card(rank = 1, box = 1, dueAt = now - 1, seen = 1))
        val session = scheduler.buildSession(catalog, cards, now, size = 6)

        assertEquals(session.size, session.map { it.rank }.toSet().size)
    }

    @Test
    fun `ignora progreso de palabras que ya no estan en el catalogo`() {
        val cards = mapOf(9999 to Card(rank = 9999, box = 1, dueAt = now - 1, seen = 1))
        val session = scheduler.buildSession(catalog, cards, now, size = 3)

        assertTrue(session.none { it.rank == 9999 })
        assertEquals(3, session.size)
    }

    @Test
    fun `dueCount no cuenta las que aun no se han visto`() {
        val cards = mapOf(
            1 to Card(rank = 1, dueAt = now - 1, seen = 0),
            2 to Card(rank = 2, dueAt = now - 1, seen = 3),
            3 to Card(rank = 3, dueAt = now + 1, seen = 3)
        )
        assertEquals(1, scheduler.dueCount(cards, now))
    }
}
