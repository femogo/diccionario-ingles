package com.femogo.vocab.engine

/**
 * Lee el diccionario en el formato que produce la generación con un modelo:
 *
 *     rank|en|lemma|pos|es|es_alt|cefr|hint
 *
 * Se usa tubería en vez de coma porque las traducciones llevan comas con
 * frecuencia y las comillas de CSV se pierden al copiar y pegar texto.
 *
 * El parser es deliberadamente tolerante: una línea mal formada se descarta y se
 * anota, pero no tumba la importación. Un diccionario de miles de entradas
 * generadas siempre trae algún resto.
 */
object WordParser {

    data class Result(val words: List<Word>, val skipped: List<String>)

    fun parse(lines: Sequence<String>): Result {
        val words = ArrayList<Word>()
        val skipped = ArrayList<String>()
        val seenRanks = HashSet<Int>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val f = line.split('|')
            if (f.size != 8) { skipped.add(line); continue }

            val rank = f[0].trim().toIntOrNull()
            val pos = Pos.from(f[3])
            val cefr = Cefr.from(f[6])
            val es = f[4].trim()

            // pos nula cubre tanto "drop" (nombres propios, siglas, ruido) como
            // etiquetas que el generador se haya inventado. Ambas se descartan.
            if (rank == null || pos == null || cefr == null || es.isEmpty() || es == "-") {
                skipped.add(line); continue
            }
            if (!seenRanks.add(rank)) { skipped.add(line); continue }

            words.add(
                Word(
                    rank = rank,
                    en = f[1].trim().lowercase(),
                    lemma = f[2].trim().lowercase().ifEmpty { f[1].trim().lowercase() },
                    pos = pos,
                    es = es.lowercase(),
                    esAlt = f[5].split(';')
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() && it != "-" },
                    cefr = cefr,
                    hint = f[7].trim().takeIf { it.isNotEmpty() && it != "-" }
                )
            )
        }
        return Result(words, skipped)
    }
}
