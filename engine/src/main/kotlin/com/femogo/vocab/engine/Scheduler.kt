package com.femogo.vocab.engine

/**
 * Decide qué palabras tocan ahora.
 *
 * El juego no tiene tandas ni final: mientras haya diccionario, siempre hay
 * siguiente pregunta. Por eso el orden de preferencia acaba cubriendo todos los
 * casos en vez de quedarse corto.
 *
 * 1. Lo que ya venció, empezando por las cajas bajas: son las peor sabidas.
 * 2. Palabras nuevas, por orden de frecuencia real.
 * 3. Lo que vence más pronto, aunque todavía no toque.
 *
 * El tercer paso incluye a propósito las palabras ya dominadas. Adelantar un
 * repaso de algo asentado es peor que repasar lo flojo, pero es mejor que
 * dejar al usuario mirando una pantalla vacía.
 */
class Scheduler(private val leitner: Leitner) {

    fun buildQueue(
        catalog: List<Word>,
        cards: Map<Int, Card>,
        now: Long,
        size: Int
    ): List<Word> {
        if (size <= 0 || catalog.isEmpty()) return emptyList()
        val byRank = catalog.associateBy { it.rank }
        val elegidas = LinkedHashSet<Int>()

        fun añadir(ranks: Sequence<Int>) {
            for (rank in ranks) {
                if (elegidas.size >= size) return
                if (rank in byRank) elegidas.add(rank)
            }
        }

        añadir(
            cards.values
                .filter { it.dueAt <= now && !it.isNew }
                .sortedWith(compareBy({ it.box }, { it.dueAt }))
                .asSequence()
                .map { it.rank }
        )

        añadir(
            catalog.asSequence()
                .sortedBy { it.rank }
                .filter { cards[it.rank]?.isNew != false }
                .map { it.rank }
        )

        añadir(
            cards.values
                .sortedWith(compareBy({ leitner.isMastered(it) }, { it.dueAt }))
                .asSequence()
                .map { it.rank }
        )

        return elegidas.mapNotNull { byRank[it] }
    }

    /** Cuántas palabras están vencidas ahora mismo. */
    fun dueCount(cards: Map<Int, Card>, now: Long): Int =
        cards.values.count { !it.isNew && it.dueAt <= now }
}
