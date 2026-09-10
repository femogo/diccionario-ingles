package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NivelProgresoTest {
    private val leitner = Leitner()
    private val progreso = ProgresoNivel(leitner)

    private fun catalogoPorNivel(porNivel: Int) = Cefr.entries.flatMapIndexed { i, cefr ->
        (1..porNivel).map { word(rank = i * 100 + it, cefr = cefr) }
    }

    @Test
    fun `sin nada jugado todos los niveles estan a cero`() {
        val p = progreso.porNivel(catalogoPorNivel(10), emptyMap())
        assertEquals(6, p.size)
        assertTrue(p.all { it.dominio == 0f && it.vistas == 0 })
        assertEquals(Cefr.A1, progreso.nivelAlcanzado(p))
    }

    @Test
    fun `una palabra a medias aporta la mitad, no cero`() {
        // Con criterio de todo o nada esto seria 0 y el usuario no veria avance.
        val catalogo = listOf(word(rank = 1, cefr = Cefr.A1), word(rank = 2, cefr = Cefr.A1))
        val cards = mapOf(1 to Card(1, box = 4, seen = 3))   // caja 4 de 6 -> 0,6
        val a1 = progreso.porNivel(catalogo, cards).first { it.cefr == Cefr.A1 }

        assertEquals(0.3f, a1.dominio, 0.01f, "0,6 repartido entre las dos palabras del nivel")
        assertEquals(1, a1.vistas)
        assertEquals(2, a1.total)
    }

    @Test
    fun `las palabras sin ver cuentan como cero`() {
        val catalogo = (1..10).map { word(rank = it, cefr = Cefr.A1) }
        val cards = mapOf(1 to Card(1, box = leitner.boxCount, seen = 9))
        val a1 = progreso.porNivel(catalogo, cards).first { it.cefr == Cefr.A1 }

        assertEquals(0.1f, a1.dominio, 0.01f, "una de diez dominada es un décimo del nivel")
    }

    @Test
    fun `un nivel entero dominado llega a uno`() {
        val catalogo = (1..5).map { word(rank = it, cefr = Cefr.B1) }
        val cards = catalogo.associate { it.rank to Card(it.rank, box = leitner.boxCount, seen = 9) }
        val b1 = progreso.porNivel(catalogo, cards).first { it.cefr == Cefr.B1 }

        assertEquals(1f, b1.dominio, 0.001f)
    }

    @Test
    fun `no se salta un nivel por muchas palabras sueltas de arriba que se sepan`() {
        val catalogo = catalogoPorNivel(10)
        // C1 entero dominado, A1 sin tocar.
        val cards = catalogo.filter { it.cefr == Cefr.C1 }
            .associate { it.rank to Card(it.rank, box = leitner.boxCount, seen = 9) }

        assertEquals(Cefr.A1, progreso.nivelAlcanzado(progreso.porNivel(catalogo, cards)))
    }

    @Test
    fun `el nivel sube cuando la base esta asentada`() {
        val catalogo = catalogoPorNivel(10)
        val cards = catalogo.filter { it.cefr in setOf(Cefr.A1, Cefr.A2) }
            .associate { it.rank to Card(it.rank, box = leitner.boxCount, seen = 9) }

        assertEquals(Cefr.A2, progreso.nivelAlcanzado(progreso.porNivel(catalogo, cards)))
    }

    @Test
    fun `los niveles vacios no cortan la progresion`() {
        // Un diccionario recortado puede no tener ninguna palabra de C2.
        val catalogo = catalogoPorNivel(4).filter { it.cefr != Cefr.C1 }
        val cards = catalogo.associate { it.rank to Card(it.rank, box = leitner.boxCount, seen = 9) }

        assertEquals(Cefr.C2, progreso.nivelAlcanzado(progreso.porNivel(catalogo, cards)))
    }

    @Test
    fun `devuelve siempre los seis niveles aunque el diccionario no los tenga`() {
        val p = progreso.porNivel(listOf(word(rank = 1, cefr = Cefr.A1)), emptyMap())
        assertEquals(Cefr.entries.toList(), p.map { it.cefr })
    }
}
