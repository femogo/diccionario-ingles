package com.femogo.vocab.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizBuilderTest {
    private val builder = QuizBuilder(Random(42))

    @Test
    fun `la opcion marcada como correcta es la traduccion buena`() {
        val target = word(10, en = "house", es = "casa")
        val q = builder.build(target, Direction.EN_TO_ES, catalog(200) + target)

        assertEquals(4, q.options.size)
        assertEquals("casa", q.correctOption)
        assertEquals("house", q.prompt)
    }

    @Test
    fun `en sentido inverso se pregunta el espanol y se responde el ingles`() {
        val target = word(10, en = "house", es = "casa")
        val q = builder.build(target, Direction.ES_TO_EN, catalog(200) + target)

        assertEquals("casa", q.prompt)
        assertEquals("house", q.correctOption)
    }

    @Test
    fun `ningun distractor comparte traduccion con la respuesta`() {
        val target = word(10, en = "bank", es = "banco", esAlt = listOf("entidad"))
        val trap1 = word(11, en = "shore", es = "banco")      // misma traducción
        val trap2 = word(12, en = "body", es = "entidad")     // choca con la alternativa
        val pool = catalog(100) + target + trap1 + trap2

        repeat(60) {
            val q = builder.build(target, Direction.EN_TO_ES, pool)
            assertFalse("banco" in q.options.filterIndexed { i, _ -> i != q.correctIndex })
            assertFalse("entidad" in q.options)
        }
    }

    @Test
    fun `los distractores prefieren la misma categoria gramatical`() {
        val target = word(50, pos = Pos.VERB, es = "correr")
        val pool = catalog(300) { if (it % 3 == 0) Pos.VERB else Pos.NOUN } + target

        val sameKind = (1..40).sumOf { seed ->
            QuizBuilder(Random(seed)).pickDistractors(target, pool, 3).count { it.pos == Pos.VERB }
        }
        assertEquals(120, sameKind, "con verbos de sobra, los 3 distractores deben ser verbos")
    }

    @Test
    fun `los distractores salen de una banda de frecuencia parecida`() {
        val target = word(500)
        val pool = catalog(2000) + target

        val distances = (1..30).flatMap { seed ->
            QuizBuilder(Random(seed)).pickDistractors(target, pool, 3).map { kotlin.math.abs(it.rank - 500) }
        }
        assertTrue(distances.max() <= 40, "esperado dentro de la vecindad, fue ${distances.max()}")
    }

    @Test
    fun `afloja el filtro cuando no hay bastantes de la misma categoria`() {
        val target = word(1, pos = Pos.INTERJ)
        val pool = listOf(target) + catalog(10) { Pos.NOUN }

        val q = builder.build(target, Direction.EN_TO_ES, pool)
        assertEquals(4, q.options.size)
        assertEquals(target.es, q.correctOption)
    }

    @Test
    fun `con un diccionario minusculo devuelve menos opciones en vez de fallar`() {
        val target = word(1, es = "uno")
        val q = builder.build(target, Direction.EN_TO_ES, listOf(target, word(2, es = "dos")))

        assertEquals(2, q.options.size)
        assertTrue(q.correctIndex in q.options.indices)
        assertEquals("uno", q.correctOption)
    }

    @Test
    fun `la respuesta correcta no cae siempre en la misma posicion`() {
        val target = word(10, es = "casa")
        val pool = catalog(200) + target
        val positions = (1..80).map {
            QuizBuilder(Random(it)).build(target, Direction.EN_TO_ES, pool).correctIndex
        }.toSet()

        assertEquals(setOf(0, 1, 2, 3), positions)
    }

    @Test
    fun `la pista se omite cuando no aporta`() {
        val sin = builder.build(word(5, hint = "-"), Direction.EN_TO_ES, catalog(50))
        val con = builder.build(word(5, hint = "financial institution"), Direction.EN_TO_ES, catalog(50))

        assertEquals(null, sin.hint)
        assertEquals("financial institution", con.hint)
    }
}
