package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColaDePreguntasTest {
    private val leitner = Leitner()
    private val now = 1_700_000_000_000L
    private val catalogo = catalog(500)

    private fun cola(distancia: IntRange = 8..16) =
        ColaDePreguntas(Scheduler(leitner), distanciaReintento = distancia, random = Random(5))

    @Test
    fun `sirve preguntas sin agotarse`() {
        val cola = cola()
        repeat(200) {
            assertNotNull(cola.siguiente(catalogo, emptyMap(), now, null), "se quedó seca en la $it")
        }
    }

    @Test
    fun `sin diccionario no hay pregunta`() {
        assertNull(cola().siguiente(emptyList(), emptyMap(), now, null))
    }

    @Test
    fun `lo fallado vuelve a los pocos turnos`() {
        val cola = cola(distancia = 10..10)
        val fallada = cola.siguiente(catalogo, emptyMap(), now, null)!!
        cola.reintentar(fallada)

        val siguientes = (1..20).mapNotNull { cola.siguiente(catalogo, emptyMap(), now, null) }
        val posicion = siguientes.indexOfFirst { it.rank == fallada.rank }

        assertEquals(10, posicion, "tenía que reaparecer diez preguntas después")
    }

    @Test
    fun `el reintento no depende del reloj`() {
        // Es justo lo que fallaba: a tres segundos por palabra, los diez minutos
        // de la primera caja son doscientas preguntas de espera.
        val cola = cola(distancia = 12..12)
        val fallada = cola.siguiente(catalogo, emptyMap(), now, null)!!
        val cards = mapOf(fallada.rank to leitner.answer(
            leitner.newCard(fallada.rank, now), correct = false, now = now
        ))
        cola.reintentar(fallada)

        // Sin avanzar el reloj ni un segundo.
        val siguientes = (1..20).mapNotNull { cola.siguiente(catalogo, cards, now, null) }
        assertTrue(siguientes.any { it.rank == fallada.rank })
    }

    @Test
    fun `no se pregunta lo mismo dos veces seguidas`() {
        val cola = cola()
        val actual = cola.siguiente(catalogo, emptyMap(), now, null)!!
        cola.reintentar(actual)

        assertTrue(
            cola.siguiente(catalogo, emptyMap(), now, null)!!.rank != actual.rank,
            "contestar de memoria no enseña nada"
        )
    }

    @Test
    fun `reintentar algo que ya esta en la cola no lo duplica`() {
        val cola = cola()
        repeat(10) { cola.siguiente(catalogo, emptyMap(), now, null) }
        val antes = cola.pendientes

        // La primera de la cola sigue pendiente; pedir su reintento no debe colarla otra vez.
        val pendiente = cola.siguiente(catalogo, emptyMap(), now, null)!!
        cola.reintentar(pendiente)
        cola.reintentar(pendiente)

        assertEquals(antes, cola.pendientes, "una palabra, una entrada")
    }

    @Test
    fun `con la cola casi vacia el reintento no se sale del final`() {
        val cola = cola(distancia = 30..30)
        val palabra = word(rank = 1)
        cola.reintentar(palabra)

        assertEquals(1, cola.pendientes)
        assertEquals(1, cola.siguiente(listOf(palabra), emptyMap(), now, null)?.rank)
    }
}
