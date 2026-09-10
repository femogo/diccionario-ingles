package com.femogo.vocab.engine

/** Categoría gramatical. El orden de declaración no importa. */
enum class Pos { NOUN, VERB, ADJ, ADV, PRON, PREP, CONJ, DET, NUM, INTERJ, PHRASAL;
    companion object {
        fun from(raw: String): Pos? = when (raw.trim().lowercase()) {
            "noun" -> NOUN; "verb" -> VERB; "adj" -> ADJ; "adv" -> ADV
            "pron" -> PRON; "prep" -> PREP; "conj" -> CONJ; "det" -> DET
            "num" -> NUM; "interj" -> INTERJ; "phrasal" -> PHRASAL
            else -> null
        }
    }
}

enum class Cefr { A1, A2, B1, B2, C1, C2;
    companion object {
        fun from(raw: String): Cefr? = entries.firstOrNull { it.name.equals(raw.trim(), true) }
    }
}

/**
 * Una entrada del diccionario.
 *
 * [rank] es la posición en la lista de frecuencia: 1 es la palabra más común del
 * inglés. Se usa para elegir distractores de dificultad parecida, así que es un
 * dato del juego, no solo un identificador.
 */
data class Word(
    val rank: Int,
    val en: String,
    val lemma: String,
    val pos: Pos,
    val es: String,
    val esAlt: List<String>,
    val cefr: Cefr,
    val hint: String?
) {
    /** Todas las traducciones que se aceptan como correctas. */
    val allEs: List<String> get() = listOf(es) + esAlt
}

/** En qué dirección se pregunta. */
enum class Direction { EN_TO_ES, ES_TO_EN }

/** Progreso del usuario sobre una palabra. */
data class Card(
    val rank: Int,
    val box: Int = 1,
    val dueAt: Long = 0L,
    val seen: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
    val lastSeenAt: Long = 0L
) {
    val isNew: Boolean get() = seen == 0
    val accuracy: Double get() = if (seen == 0) 0.0 else correct.toDouble() / seen
}

/** Una pregunta lista para pintar en pantalla. */
data class Question(
    val word: Word,
    val direction: Direction,
    val prompt: String,
    val hint: String?,
    val options: List<String>,
    val correctIndex: Int
) {
    val correctOption: String get() = options[correctIndex]
}
