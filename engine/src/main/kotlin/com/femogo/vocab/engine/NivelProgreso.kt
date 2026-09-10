package com.femogo.vocab.engine

/** Cuánto se domina un nivel del marco europeo. [dominio] va de 0 a 1. */
data class NivelProgreso(
    val cefr: Cefr,
    val total: Int,
    val vistas: Int,
    val dominio: Float
)

/**
 * Estima en qué nivel anda el usuario a partir de lo que lleva asentado.
 *
 * No mide con cajas dominadas o no dominadas, sino con el avance dentro de la
 * escala: una palabra en la caja 3 de 6 aporta la mitad. Con el criterio de
 * todo o nada, un usuario con cientos de palabras a medias vería todo en cero
 * durante semanas, que es justo cuando más falta hace ver que se avanza.
 *
 * Las palabras del diccionario que aún no se han visto cuentan como cero. El
 * nivel no es lo que sabes de lo que has tocado, sino de todo lo que hay.
 */
class ProgresoNivel(private val leitner: Leitner) {

    fun porNivel(catalog: List<Word>, cards: Map<Int, Card>): List<NivelProgreso> {
        val porCefr = catalog.groupBy { it.cefr }
        return Cefr.entries.map { cefr ->
            val palabras = porCefr[cefr].orEmpty()
            if (palabras.isEmpty()) return@map NivelProgreso(cefr, 0, 0, 0f)
            var suma = 0f
            var vistas = 0
            for (w in palabras) {
                val card = cards[w.rank]
                if (card != null && !card.isNew) {
                    vistas++
                    suma += avanceDe(card)
                }
            }
            NivelProgreso(cefr, palabras.size, vistas, suma / palabras.size)
        }
    }

    /** Posición de la palabra dentro de la escala de cajas, de 0 a 1. */
    private fun avanceDe(card: Card): Float =
        if (leitner.boxCount <= 1) 1f
        else (card.box - 1).toFloat() / (leitner.boxCount - 1)

    /**
     * El nivel alcanzado es el más alto cuya base está asentada, exigiendo
     * además que todos los anteriores lo estén: no se llega a B2 salteándose
     * A2, por muchas palabras sueltas que se acierten.
     */
    fun nivelAlcanzado(progresos: List<NivelProgreso>): Cefr {
        var alcanzado = Cefr.A1
        for (p in progresos) {
            if (p.total > 0 && p.dominio < UMBRAL) return alcanzado
            if (p.total > 0) alcanzado = p.cefr
        }
        return alcanzado
    }

    companion object {
        /** Un nivel cuenta como asentado a partir de la mitad de su escala. */
        const val UMBRAL = 0.5f
    }
}
