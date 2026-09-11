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
    fun `los repasos vencidos entran en la cola`() {
        val cards = mapOf(
            80 to Card(rank = 80, box = 2, dueAt = now - 1000, seen = 3),
            90 to Card(rank = 90, box = 3, dueAt = now - 5000, seen = 4)
        )
        val cola = scheduler.buildQueue(catalog, cards, now, size = 5).map { it.rank }

        assertTrue(80 in cola && 90 in cola, "lo vencido no puede quedarse fuera")
    }

    @Test
    fun `entre vencidas manda la caja mas baja`() {
        val cards = (1..20).associateWith { Card(it, box = 4, dueAt = now - 100, seen = 8) }
            .toMutableMap()
        cards[20] = Card(20, box = 1, dueAt = now - 10, seen = 2)

        val vencidasEnCola = scheduler.buildQueue(catalog, cards, now, size = 6)
            .map { it.rank }
            .filter { it in cards }

        assertEquals(20, vencidasEnCola.first(), "lo que peor se sabe se repasa antes")
    }

    @Test
    fun `la cola mezcla novedades con repasos en vez de agruparlas`() {
        // Diez palabras nuevas seguidas es donde se abandona.
        val cards = (1..30).associateWith { Card(it, box = 2, dueAt = now - 100, seen = 2) }
        val cola = scheduler.buildQueue(catalog, cards, now, size = 20)
        val esNueva = cola.map { it.rank !in cards }

        val rachaMax = esNueva.fold(0 to 0) { (max, actual), nueva ->
            val siguiente = if (nueva) actual + 1 else 0
            maxOf(max, siguiente) to siguiente
        }.first
        assertTrue(rachaMax <= 3, "racha de novedades seguidas: $rachaMax")
    }

    @Test
    fun `las palabras nuevas entran por orden de frecuencia`() {
        val cola = scheduler.buildQueue(catalog, emptyMap(), now, size = 4)
        assertEquals(listOf(1, 2, 3, 4), cola.map { it.rank })
    }

    @Test
    fun `las novedades no salen agrupadas por nivel`() {
        // Diez palabras de A1 seguidas y luego diez de A2 se nota al jugar.
        val variado = Cefr.entries.flatMapIndexed { i, cefr ->
            (1..200).map { word(rank = i * 200 + it, cefr = cefr) }
        }
        val cola = scheduler.buildQueue(variado, emptyMap(), now, size = 30)
        val niveles = cola.map { it.cefr }

        val rachaMax = niveles.fold(Triple(0, 0, null as Cefr?)) { (max, actual, previo), cefr ->
            val seguidas = if (cefr == previo) actual + 1 else 1
            Triple(maxOf(max, seguidas), seguidas, cefr)
        }.first

        println("secuencia de niveles: " + niveles.joinToString(" "))
        println("racha máxima del mismo nivel: $rachaMax")
        assertTrue(
            rachaMax <= 12,
            "racha de $rachaMax palabras seguidas del mismo nivel en una cola de ${cola.size}"
        )
        assertTrue(niveles.toSet().size >= 2, "y tiene que haber más de un nivel")
    }

    @Test
    fun `un nivel con una sola palabra asignada no cae siempre al final`() {
        val variado = Cefr.entries.flatMapIndexed { i, cefr ->
            (1..200).map { word(rank = i * 200 + it, cefr = cefr) }
        }
        val cola = scheduler.buildQueue(variado, emptyMap(), now, size = 30)
        val escasos = cola.map { it.cefr }
            .groupingBy { it }.eachCount()
            .filterValues { it == 1 }.keys

        escasos.forEach { cefr ->
            val posicion = cola.indexOfFirst { it.cefr == cefr }
            assertTrue(
                posicion < cola.size - 1,
                "$cefr aporta una sola palabra y quedó en la última posición"
            )
        }
    }

    @Test
    fun `reparte las novedades entre niveles en vez de agotar el primero`() {
        val variado = Cefr.entries.flatMapIndexed { i, cefr ->
            (1..50).map { word(rank = i * 50 + it, cefr = cefr) }
        }
        val niveles = scheduler.buildQueue(variado, emptyMap(), now, size = 30)
            .map { it.cefr }.toSet()

        assertTrue(niveles.size >= 2, "con el diccionario entero por delante debe variar")
        assertTrue(Cefr.A1 in niveles, "pero el grueso sigue saliendo de abajo")
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
