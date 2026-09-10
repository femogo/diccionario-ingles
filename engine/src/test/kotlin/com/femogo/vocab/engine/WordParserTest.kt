package com.femogo.vocab.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordParserTest {

    private fun parse(vararg lines: String) = WordParser.parse(lines.asSequence())

    @Test
    fun `lee una entrada completa`() {
        val w = parse("88|bank|bank|noun|banco|entidad bancaria;caja|A2|financial institution")
            .words.single()

        assertEquals(88, w.rank)
        assertEquals("bank", w.en)
        assertEquals(Pos.NOUN, w.pos)
        assertEquals("banco", w.es)
        assertEquals(listOf("entidad bancaria", "caja"), w.esAlt)
        assertEquals(Cefr.A2, w.cefr)
        assertEquals("financial institution", w.hint)
        assertEquals(listOf("banco", "entidad bancaria", "caja"), w.allEs)
    }

    @Test
    fun `descarta lo marcado como drop`() {
        val r = parse(
            "1|you|you|pron|tú|usted|A1|-",
            "4312|tokyo|tokyo|drop|-|-|-|-"
        )
        assertEquals(listOf("you"), r.words.map { it.en })
        assertEquals(1, r.skipped.size)
    }

    @Test
    fun `una linea rota no tumba la importacion`() {
        val r = parse(
            "1|you|you|pron|tú|usted|A1|-",
            "esto no es una linea valida",
            "2|the|the|det|el|la;los|A1|-",
            "3|to|to|prep||-|A1|-",
            "x|bad|bad|adj|malo|-|A1|-",
            "5|odd|odd|adj|raro|-|Z9|-"
        )
        assertEquals(listOf("you", "the"), r.words.map { it.en })
        assertEquals(4, r.skipped.size)
    }

    @Test
    fun `los rangos duplicados se descartan`() {
        val r = parse(
            "7|dog|dog|noun|perro|-|A1|-",
            "7|cat|cat|noun|gato|-|A1|-"
        )
        assertEquals(listOf("dog"), r.words.map { it.en })
        assertEquals(1, r.skipped.size)
    }

    @Test
    fun `normaliza mayusculas y guiones vacios`() {
        val w = parse("9|House|HOUSE|Noun|Casa|-|a1|-").words.single()

        assertEquals("house", w.en)
        assertEquals("casa", w.es)
        assertTrue(w.esAlt.isEmpty())
        assertNull(w.hint)
        assertEquals(Cefr.A1, w.cefr)
    }

    @Test
    fun `ignora lineas en blanco y comentarios`() {
        val r = parse("", "   ", "# generado con chatgpt", "1|a|a|det|un|una|A1|-")
        assertEquals(1, r.words.size)
        assertTrue(r.skipped.isEmpty())
    }

    @Test
    fun `si falta el lema usa la palabra`() {
        val w = parse("3|went||verb|ir|irse|A1|past tense").words.single()
        assertEquals("went", w.lemma)
    }
}
