package com.femogo.vocab.engine

/**
 * Decide qué palabras tocan ahora.
 *
 * El juego no tiene tandas ni final: mientras haya diccionario, siempre hay
 * siguiente pregunta.
 *
 * La cola se arma en tres capas. Primero una reserva de palabras nuevas,
 * repartida entre niveles por [Mezcla]; luego los repasos que ya vencieron,
 * empezando por las cajas bajas; y si aún sobra sitio, se adelanta lo que vence
 * más pronto, dominadas incluidas.
 *
 * La reserva de novedades va delante a propósito. Sin ella los repasos vencidos
 * se comen la sesión entera a las pocas semanas y el jugador deja de descubrir
 * palabras justo cuando más vocabulario podría estar construyendo.
 */
class Scheduler(
    private val leitner: Leitner,
    private val mezcla: Mezcla = Mezcla(),
    private val progresoNivel: ProgresoNivel = ProgresoNivel(leitner)
) {

    fun buildQueue(
        catalog: List<Word>,
        cards: Map<Int, Card>,
        turno: Int,
        size: Int,
        aciertoReciente: Float? = null
    ): List<Word> {
        if (size <= 0 || catalog.isEmpty()) return emptyList()
        val byRank = catalog.associateBy { it.rank }
        val elegidas = LinkedHashSet<Int>()

        fun añadir(ranks: Sequence<Int>, tope: Int = size) {
            for (rank in ranks) {
                if (elegidas.size >= tope) return
                if (rank in byRank) elegidas.add(rank)
            }
        }

        val vencidas = cards.values
            .filter { it.dueTurn <= turno && !it.isNew }
            .sortedWith(compareBy({ it.box }, { it.dueTurn }))

        val nuevasPorNivel = catalog
            .filter { cards[it.rank]?.isNew != false }
            .groupBy { it.cefr }

        val cupoNuevas = mezcla.cuantasNuevas(size, vencidas.size)
        val reparto = mezcla.repartir(
            progresos = progresoNivel.porNivel(catalog, cards),
            disponibles = nuevasPorNivel.mapValues { it.value.size },
            cuantas = cupoNuevas,
            aciertoReciente = aciertoReciente
        )

        // Dentro de cada nivel, por frecuencia de uso: lo más común primero.
        //
        // Entre niveles se entrelazan en proporción a su cuota, en vez de ir uno
        // detrás de otro. Recorrer el reparto sin más sacaba diez palabras de A1
        // seguidas y luego diez de A2, que es exactamente lo que se nota al
        // jugar. Cada palabra recibe una posición relativa dentro de la cuota de
        // su nivel, y el orden final sale de ordenar por esa posición: un nivel
        // con una sola palabra la coloca en mitad de la tanda en lugar de
        // amontonarla al final.
        val nuevas = reparto.flatMap { (cefr, cuantas) ->
            nuevasPorNivel[cefr].orEmpty()
                .sortedBy { it.rank }
                .take(cuantas)
                .mapIndexed { i, palabra -> (i + 0.5f) / cuantas to palabra }
        }.sortedBy { it.first }.map { it.second }
        añadir(nuevas.asSequence().map { it.rank }, tope = cupoNuevas)

        añadir(vencidas.asSequence().map { it.rank })

        añadir(
            cards.values
                .sortedWith(compareBy({ leitner.isMastered(it) }, { it.dueTurn }))
                .asSequence()
                .map { it.rank }
        )

        // Red de seguridad. Las capas anteriores pueden quedarse cortas: el
        // reparto por nivel se topa con niveles agotados, y el progreso puede
        // referirse a palabras que ya no están en el diccionario tras una
        // actualización. Mientras haya catálogo, la cola se completa.
        añadir(catalog.asSequence().sortedBy { it.rank }.map { it.rank })

        // Las nuevas salieron en bloque; intercalarlas evita rachas de diez
        // palabras desconocidas seguidas, que es donde se abandona.
        return barajarEnBloques(elegidas.mapNotNull { byRank[it] }, cards)
    }

    private fun barajarEnBloques(palabras: List<Word>, cards: Map<Int, Card>): List<Word> {
        val (nuevas, repasos) = palabras.partition { cards[it.rank]?.isNew != false }
        if (nuevas.isEmpty() || repasos.isEmpty()) return palabras

        val salida = ArrayList<Word>(palabras.size)
        val cadaCuantas = (palabras.size.toFloat() / nuevas.size).coerceAtLeast(1f)
        var siguienteNueva = 0f
        val itNuevas = nuevas.iterator()
        val itRepasos = repasos.iterator()

        while (salida.size < palabras.size) {
            val tocaNueva = itNuevas.hasNext() && salida.size >= siguienteNueva
            if (tocaNueva) {
                salida.add(itNuevas.next())
                siguienteNueva += cadaCuantas
            } else if (itRepasos.hasNext()) {
                salida.add(itRepasos.next())
            } else if (itNuevas.hasNext()) {
                salida.add(itNuevas.next())
            } else break
        }
        return salida
    }

    /** Cuántas palabras tocan ya. */
    fun dueCount(cards: Map<Int, Card>, turno: Int): Int =
        cards.values.count { !it.isNew && it.dueTurn <= turno }
}
