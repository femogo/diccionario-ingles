package com.femogo.vocab.engine

/** Diccionario de juguete para las pruebas. */
fun word(
    rank: Int,
    en: String = "w$rank",
    pos: Pos = Pos.NOUN,
    es: String = "p$rank",
    esAlt: List<String> = emptyList(),
    cefr: Cefr = Cefr.A1,
    hint: String? = null
) = Word(rank, en, en, pos, es, esAlt, cefr, hint)

fun catalog(size: Int, pos: (Int) -> Pos = { Pos.NOUN }): List<Word> =
    (1..size).map { word(rank = it, pos = pos(it)) }
