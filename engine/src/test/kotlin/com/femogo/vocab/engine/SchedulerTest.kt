package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulerTest {
    private val leitner = Leitner()
    private val scheduler = Scheduler(leitner)
    private val now = 1_700_000_000_000L
    private val catalog = catalog(100)

    @Test
    fun `las vencidas van antes que las nuevas`() {
        val cards = mapOf(
            80 to Card(rank = 80, box = 2, dueAt = now - 1000, seen = 3),
            90 to Card(rank = 90, box = 3, dueAt = now - 5000, seen = 4)
        )
        val cola = scheduler.buildQueue(catalog, cards, now, size = 5)
        assertEquals(listOf(80, 90), cola.take(2).map { it.rank })
    }

    @Test
    fun `entre vencidas manda la caja mas baja`() {
        val cards = mapOf(
            10 to Card(rank = 10, box = 4, dueAt = now - 9999, seen = 8),
            20 to Card(rank = 20, box = 1, dueAt = now - 10, seen = 2)
        )
        val cola = scheduler.buildQueue(catalog, cards, now, size = 2)
        assertEquals(20, cola.first().rank, "lo que peor se sabe se pregunta antes")
    }

    @Test
    fun `las palabras nuevas entran por orden de frecuencia`() {
        val cola = scheduler.buildQueue(catalog, emptyMap(), now, size = 4)
        assertEquals(listOf(1, 2, 3, 4), cola.map { it.rank })
    }

    @Test
    fun `siempre devuelve el tamano pedido mientras haya diccionario`() {
        val cola = scheduler.buildQueue(catalog, emptyMap(), now, size = 30)
        assertEquals(30, cola.size, "el juego no tiene tandas: no puede quedarse corto")
    }

    @Test
    fun `con todo dominado y sin vencer sigue habiendo preguntas`() {
        // Sin este caso la pantalla se quedaria vacia en cuanto el usuario
        // adelanta trabajo, que es justo cuando mas quiere seguir jugando.
        val cards = catalog.associate {
            it.rank to Card(it.rank, box = leitner.boxCount, dueAt = now + 1_000_000L, seen = 9)
        }
        val cola = scheduler.buildQueue(catalog, cards, now, size = 8)
        assertEquals(8, cola.size)
        assertEquals(8, cola.map { it.rank }.toSet().size, "sin repetidas")
    }

    @Test
    fun `lo no dominado se adelanta antes que lo dominado`() {
        val cards = mapOf(
            5 to Card(5, box = leitner.boxCount, dueAt = now + 1000, seen = 9),
            6 to Card(6, box = 2, dueAt = now + 5000, seen = 2)
        )
        val soloVistas = catalog.filter { it.rank in setOf(5, 6) }
        val cola = scheduler.buildQueue(soloVistas, cards, now, size = 2)
        assertEquals(6, cola.first().rank)
    }

    @Test
    fun `una palabra no sale dos veces en la misma cola`() {
        val cards = mapOf(1 to Card(1, box = 1, dueAt = now - 1, seen = 1))
        val cola = scheduler.buildQueue(catalog, cards, now, size = 6)
        assertEquals(cola.size, cola.map { it.rank }.toSet().size)
    }

    @Test
    fun `ignora progreso de palabras que ya no estan en el catalogo`() {
        val cards = mapOf(9999 to Card(9999, box = 1, dueAt = now - 1, seen = 1))
        val cola = scheduler.buildQueue(catalog, cards, now, size = 3)
        assertTrue(cola.none { it.rank == 9999 })
        assertEquals(3, cola.size)
    }

    @Test
    fun `sin diccionario no hay cola`() {
        assertTrue(scheduler.buildQueue(emptyList(), emptyMap(), now, size = 5).isEmpty())
    }

    @Test
    fun `dueCount no cuenta las que aun no se han visto`() {
        val cards = mapOf(
            1 to Card(1, dueAt = now - 1, seen = 0),
            2 to Card(2, dueAt = now - 1, seen = 3),
            3 to Card(3, dueAt = now + 1, seen = 3)
        )
        assertEquals(1, scheduler.dueCount(cards, now))
    }
}
