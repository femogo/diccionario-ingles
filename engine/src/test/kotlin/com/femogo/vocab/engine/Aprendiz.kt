package com.femogo.vocab.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Un jugador simulado que aprende y olvida.
 *
 * Existe porque las simulaciones con acierto fijo no sirven para comparar
 * configuraciones del motor: si el jugador acierta siempre lo mismo, cualquier
 * cambio que acelere el avance parece bueno, porque el coste —olvidar— no está
 * representado en ninguna parte.
 *
 * El modelo es deliberadamente sencillo y recoge tres cosas conocidas de la
 * memoria: recordar algo lo refuerza, el refuerzo es mayor cuanto más se ha
 * tardado en volver a ello, y lo no repasado se desvanece más despacio cuanto
 * más asentado estaba. No pretende ser exacto: pretende que dos escalas de
 * cajas distintas se puedan comparar con la misma vara.
 */
class Aprendiz(
    private val random: Random,
    /** Cuánto refuerza un acierto. */
    private val refuerzo: Double = 0.5,
    /** Qué parte de lo aprendido sobrevive a un fallo. */
    private val castigo: Double = 0.5,
    /**
     * Lo que se aprende de ver la respuesta correcta tras fallar.
     *
     * Sin esto el modelo no funcionaba: una palabra nueva empieza con fuerza
     * cero, y cero multiplicado por el castigo sigue siendo cero, así que el
     * jugador simulado solo aprendía las palabras que acertaba de chiripa. En
     * la aplicación real, fallar enseña: la pantalla muestra la traducción
     * buena y las alternativas.
     */
    private val aprendeAlFallar: Double = 0.45,
    /** Turnos que tarda en desvanecerse lo recién aprendido. */
    private val vidaMedia: Double = 1500.0
) {
    private val fuerza = HashMap<Int, Double>()
    private val ultimaVez = HashMap<Int, Int>()

    /** Lo que de verdad recuerda ahora mismo, sin contar la suerte. */
    fun retencion(rank: Int, turno: Int): Double {
        val f = fuerza[rank] ?: return 0.0
        val transcurrido = (turno - (ultimaVez[rank] ?: turno)).coerceAtLeast(0)
        // Cuanto más asentada está una palabra, más despacio se pierde.
        val resistencia = vidaMedia * exp(f)
        return (1 - exp(-f)) * exp(-transcurrido / resistencia)
    }

    /** Responde a una pregunta de [opciones] opciones, con su parte de suerte. */
    fun responde(rank: Int, turno: Int, opciones: Int): Boolean {
        val sabe = retencion(rank, turno)
        val probabilidad = sabe + (1 - sabe) / opciones
        return random.nextDouble() < probabilidad
    }

    fun registra(rank: Int, turno: Int, acierto: Boolean) {
        val f = fuerza[rank] ?: 0.0
        val transcurrido = (turno - (ultimaVez[rank] ?: turno)).coerceAtLeast(0)
        fuerza[rank] = if (acierto) {
            // Recuperar algo que costaba refuerza más que repetir lo que se
            // acaba de ver: es el efecto del espaciado.
            f + refuerzo * ln(1.0 + transcurrido / 20.0).coerceAtLeast(0.2)
        } else {
            f * castigo + aprendeAlFallar
        }
        ultimaVez[rank] = turno
    }

    /** Cuántas palabras recordaría si le preguntaran ahora mismo. */
    fun palabrasRecordadas(turno: Int): Double =
        fuerza.keys.sumOf { retencion(it, turno) }

    /** Las que sabe con soltura, no las que acertaría por los pelos. */
    fun palabrasSolidas(turno: Int, umbral: Double = 0.8): Int =
        fuerza.keys.count { retencion(it, turno) >= umbral }
}
