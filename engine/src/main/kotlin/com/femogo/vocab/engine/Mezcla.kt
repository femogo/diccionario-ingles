package com.femogo.vocab.engine

/**
 * Decide de qué nivel sale cada palabra nueva y cuántas caras nuevas entran
 * frente a repasos.
 *
 * El reparto por nivel no es una tabla fija: el peso de un nivel es lo que le
 * falta por dominar, atenuado por lo abierto que esté. Así A1 acapara al
 * principio y va cediendo sitio a A2 y B1 por sí solo, sin números escritos a
 * mano que haya que recalibrar cada vez que cambia el diccionario.
 */
class Mezcla(
    /** Parte de cada sesión reservada a palabras que no se han visto nunca. */
    private val proporcionNuevas: Float = 0.35f,
    /** Acierto al que se apunta. Por encima se abre la mano, por debajo se cierra. */
    private val aciertoObjetivo: Float = 0.80f,
    /** Parte que sigue siendo nueva por muchos repasos que se acumulen. */
    private val minimoNovedad: Float = MINIMO_NOVEDAD
) {

    /**
     * Cuántas de las [total] preguntas deben ser palabras nuevas.
     *
     * Hay dos formas de estropear esto y la reserva navega entre ambas.
     *
     * Sin reserva, los repasos vencidos acaban ocupando la sesión entera y el
     * jugador deja de descubrir palabras a las pocas semanas.
     *
     * Con reserva fija pasa lo contrario y es peor: entran palabras nuevas más
     * deprisa de lo que se pueden repasar, la deuda de repasos crece sin límite
     * y nada llega nunca a las cajas altas. Medido en simulación, a los tres
     * meses se habían visto tres veces más palabras y ninguna estaba aprendida.
     *
     * Así que la reserva cede en cuanto hay atasco: se divide por lo desbordada
     * que esté la capacidad de repaso, y solo conserva un mínimo para que el
     * descubrimiento nunca se pare del todo.
     */
    fun cuantasNuevas(total: Int, vencidas: Int): Int {
        val huecoLibre = (total - vencidas).coerceAtLeast(0)
        val reserva = total * proporcionNuevas
        // Sin deuda: todo lo que no sea repaso pendiente se llena con novedades.
        if (huecoLibre >= reserva) return huecoLibre.coerceAtMost(total)

        val capacidadRepaso = total * (1f - proporcionNuevas)
        val presion = (vencidas / capacidadRepaso).coerceAtLeast(1f)
        val minimo = maxOf(1, Math.round(total * minimoNovedad))
        return Math.round(reserva / presion).coerceIn(minimo, total)
    }

    /**
     * Reparte [cuantas] palabras nuevas entre los niveles.
     *
     * [aciertoReciente] mueve el centro de gravedad: acertando mucho se mezcla
     * más material de arriba, fallando se concentra abajo. Es el único mando y
     * se ajusta solo.
     */
    fun repartir(
        progresos: List<NivelProgreso>,
        disponibles: Map<Cefr, Int>,
        cuantas: Int,
        aciertoReciente: Float?
    ): Map<Cefr, Int> {
        if (cuantas <= 0) return emptyMap()

        val decaimiento = decaimientoPara(aciertoReciente)
        val dominio = progresos.associate { it.cefr to it.dominio }
        val pesos = LinkedHashMap<Cefr, Float>()

        Cefr.entries.forEachIndexed { i, cefr ->
            if ((disponibles[cefr] ?: 0) <= 0) return@forEachIndexed
            // Lo que le falta al nivel por dominar. Un nivel ya asentado deja de
            // pedir palabras nuevas, pero nunca baja a cero del todo.
            val falta = (1f - (dominio[cefr] ?: 0f)).coerceAtLeast(0.05f)
            // Cuánto se ha ganado el nivel el derecho a aparecer: por defecto
            // decae con la distancia, y se abre del todo en cuanto el anterior
            // tiene una base.
            val anterior = if (i == 0) 1f else (dominio[Cefr.entries[i - 1]] ?: 0f) / APERTURA
            val apertura = maxOf(Math.pow(decaimiento.toDouble(), i.toDouble()).toFloat(),
                                 anterior.coerceIn(0f, 1f))
            pesos[cefr] = falta * apertura
        }

        val suma = pesos.values.sum()
        if (suma <= 0f) return emptyMap()

        // Reparto proporcional, y los restos al nivel con más peso pendiente.
        val reparto = LinkedHashMap<Cefr, Int>()
        var asignadas = 0
        pesos.forEach { (cefr, peso) ->
            val n = Math.floor((cuantas * peso / suma).toDouble()).toInt()
                .coerceAtMost(disponibles[cefr] ?: 0)
            if (n > 0) { reparto[cefr] = n; asignadas += n }
        }
        // Repartir los restos da varias vueltas a propósito: cuando un nivel se
        // queda sin palabras, lo que le sobra tiene que acabar en otro, no
        // perderse y devolver menos preguntas de las pedidas.
        var sobran = cuantas - asignadas
        val porPeso = pesos.entries.sortedByDescending { it.value }.map { it.key }
        while (sobran > 0) {
            var repartidaAlguna = false
            for (cefr in porPeso) {
                if (sobran <= 0) break
                val tope = disponibles[cefr] ?: 0
                val actual = reparto[cefr] ?: 0
                if (actual < tope) {
                    reparto[cefr] = actual + 1
                    sobran--
                    repartidaAlguna = true
                }
            }
            // Todos los niveles al tope: no hay más palabras nuevas que dar.
            if (!repartidaAlguna) break
        }
        return reparto
    }

    /**
     * Con qué rapidez pierde peso cada nivel respecto al anterior. Más alto
     * significa más mezcla de material difícil.
     */
    private fun decaimientoPara(aciertoReciente: Float?): Float = when {
        aciertoReciente == null -> BASE
        aciertoReciente > aciertoObjetivo + MARGEN -> BASE + 0.15f
        aciertoReciente < aciertoObjetivo - MARGEN -> BASE - 0.15f
        else -> BASE
    }

    private companion object {
        /** Dominio del nivel anterior a partir del cual el siguiente abre del todo. */
        const val APERTURA = 0.3f
        const val BASE = 0.5f
        const val MARGEN = 0.05f
        /**
         * Parte de la sesión que sigue siendo nueva por muchos repasos que se
         * acumulen.
         *
         * Medido en partidas de veinte mil preguntas contra un jugador que
         * olvida, contando palabras que sabría si le preguntaran al final:
         *
         *     10 % -> 2574    15 % -> 2618    20 % -> 2757
         *     25 % -> 2113    30 % -> 1075
         *
         * El máximo está en el 20 %, y no se coge. Justo después hay un
         * derrumbe: cinco puntos más y se pierden seiscientas palabras, porque
         * entran más deprisa de lo que se pueden asentar. El 20 % es la cima de
         * un acantilado calculada por un modelo aproximado, así que el ajuste se
         * queda un escalón antes: rinde un 5 % menos y, si el modelo se
         * equivoca, sigue subiendo en vez de desplomarse.
         */
        const val MINIMO_NOVEDAD = 0.15f
    }
}
