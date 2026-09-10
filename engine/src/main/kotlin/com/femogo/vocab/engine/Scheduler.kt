package com.femogo.vocab.engine

/**
 * Decide qué palabras tocan ahora.
 *
 * Prioridad: primero lo que ya venció, luego palabras nuevas por orden de
 * frecuencia, y solo si sobra sitio se adelantan revisiones futuras. Ese último
 * paso existe para que la aplicación nunca se quede sin nada que preguntar
 * cuando el usuario quiere seguir jugando.
 */
class Scheduler(
    private val leitner: Leitner,
    /** Tope de palabras nuevas por día. Sin tope, el usuario se satura. */
    private val newPerDay: Int = 20
) {
    fun buildSession(
        catalog: List<Word>,
        cards: Map<Int, Card>,
        now: Long,
        size: Int,
        newIntroducedToday: Int = 0
    ): List<Word> {
        if (size <= 0 || catalog.isEmpty()) return emptyList()
        val byRank = catalog.associateBy { it.rank }
        val picked = LinkedHashSet<Int>()

        // 1. Vencidas. Las de caja baja van delante: son las que peor se saben.
        cards.values
            .filter { it.dueAt <= now && !it.isNew }
            .sortedWith(compareBy({ it.box }, { it.dueAt }))
            .forEach { if (picked.size < size && it.rank in byRank) picked.add(it.rank) }

        // 2. Nuevas, por frecuencia, respetando el tope diario.
        var margin = (newPerDay - newIntroducedToday).coerceAtLeast(0)
        if (picked.size < size && margin > 0) {
            for (word in catalog.sortedBy { it.rank }) {
                if (picked.size >= size || margin == 0) break
                val card = cards[word.rank]
                if (card == null || card.isNew) {
                    if (picked.add(word.rank)) margin--
                }
            }
        }

        // 3. Adelantar lo que vence más pronto, para no dejar la sesión coja.
        if (picked.size < size) {
            cards.values
                .filter { it.rank !in picked && !leitner.isMastered(it) }
                .sortedBy { it.dueAt }
                .forEach { if (picked.size < size && it.rank in byRank) picked.add(it.rank) }
        }

        return picked.mapNotNull { byRank[it] }
    }

    /** Cuántas palabras están vencidas ahora mismo. */
    fun dueCount(cards: Map<Int, Card>, now: Long): Int =
        cards.values.count { !it.isNew && it.dueAt <= now }
}
