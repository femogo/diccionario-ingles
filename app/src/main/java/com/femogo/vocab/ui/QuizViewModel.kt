package com.femogo.vocab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.femogo.vocab.VocabApplication
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.Cefr
import com.femogo.vocab.engine.ColaDePreguntas
import com.femogo.vocab.engine.DirectionMode
import com.femogo.vocab.engine.NivelProgreso
import com.femogo.vocab.engine.ProgresoNivel
import com.femogo.vocab.engine.Leitner
import com.femogo.vocab.engine.Question
import com.femogo.vocab.engine.QuizBuilder
import com.femogo.vocab.engine.Scheduler
import com.femogo.vocab.engine.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuizUiState(
    val loading: Boolean = true,
    val question: Question? = null,
    /** Índice pulsado, o null si la pregunta sigue abierta. */
    val chosenIndex: Int? = null,
    val sinDiccionario: Boolean = false,
    val niveles: List<NivelProgreso> = emptyList(),
    val nivelAlcanzado: Cefr = Cefr.A1
) {
    val answered: Boolean get() = chosenIndex != null
    val wasCorrect: Boolean get() = chosenIndex != null && chosenIndex == question?.correctIndex
}

/**
 * El juego no tiene tandas: la cola se rellena sola antes de agotarse, así que
 * nunca aparece una pantalla de "sesión terminada" ni un final al que llegar.
 */
class QuizViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as VocabApplication).repository
    private val settingsStore = (app as VocabApplication).settings

    private val leitner = Leitner()
    private val quizBuilder = QuizBuilder()
    private val scheduler = Scheduler(leitner)
    private val progresoNivel = ProgresoNivel(leitner)
    private val cola = ColaDePreguntas(scheduler)

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var catalog: List<Word> = emptyList()
    private val cards = mutableMapOf<Int, Card>()
    private var optionCount = 4

    /** Preguntas respondidas en total. Es el reloj del juego. */
    private var turno = 0

    // Ventana corta de resultados. El planificador la usa para mezclar más
    // material difícil cuando se va sobrado, y menos cuando se atasca. Un
    // porcentaje de toda la vida no serviría: tardaría semanas en moverse.
    private val ultimas = ArrayDeque<Boolean>()

    private val aciertoReciente: Float?
        get() = if (ultimas.size < MINIMO_PARA_AJUSTAR) null
        else ultimas.count { it }.toFloat() / ultimas.size

    init { arrancar() }

    fun arrancar() {
        viewModelScope.launch {
            _state.value = QuizUiState(loading = true)
            repo.ensureSeeded()
            catalog = repo.catalog()
            cards.clear()
            cards.putAll(repo.cards())
            val guardado = settingsStore.flow.first()
            optionCount = guardado.optionCount
            turno = guardado.turno
            ultimas.clear()
            guardado.ultimasRespuestas.forEach { ultimas.addLast(it == '1') }

            if (catalog.isEmpty()) {
                _state.value = QuizUiState(loading = false, sinDiccionario = true)
                return@launch
            }
            cola.vaciar()
            _state.value = QuizUiState(loading = false)
            recalcularNivel()
            mostrarSiguiente()
        }
    }

    /** Se llama al recargar el diccionario, para no seguir preguntando lo viejo. */
    fun recargarCatalogo() {
        viewModelScope.launch {
            catalog = repo.catalog()
            cola.vaciar()
            if (catalog.isNotEmpty()) {
                _state.value = _state.value.copy(sinDiccionario = false)
                recalcularNivel()
                mostrarSiguiente()
            }
        }
    }

    private fun mostrarSiguiente() {
        val word = cola.siguiente(catalog, cards, turno, aciertoReciente)
        if (word == null) {
            _state.value = _state.value.copy(question = null, sinDiccionario = catalog.isEmpty())
            return
        }
        val card = cards[word.rank] ?: leitner.newCard(word.rank)
        _state.value = _state.value.copy(
            question = quizBuilder.build(
                target = word,
                direction = leitner.directionFor(card, DirectionMode.PROGRESSIVE),
                pool = catalog,
                optionCount = optionCount
            ),
            chosenIndex = null
        )
    }

    fun answer(chosen: Int) {
        val actual = _state.value
        // Ignorar pulsaciones repetidas: la respuesta ya se contabilizó.
        if (actual.answered || actual.question == null) return

        val question = actual.question
        val acierto = chosen == question.correctIndex
        turno++
        val card = cards[question.word.rank] ?: leitner.newCard(question.word.rank)
        val actualizada = leitner.answer(card, acierto, turno)
        cards[question.word.rank] = actualizada

        // Lo fallado vuelve unas preguntas después, no dentro de diez minutos:
        // a ritmo rápido esos minutos son cientos de preguntas de espera.
        if (!acierto) cola.reintentar(question.word)

        ultimas.addLast(acierto)
        while (ultimas.size > VENTANA) ultimas.removeFirst()
        val instantanea = ultimas.joinToString("") { if (it) "1" else "0" }

        _state.value = actual.copy(chosenIndex = chosen)
        recalcularNivel()
        val turnoActual = turno
        viewModelScope.launch {
            repo.save(actualizada)
            settingsStore.setUltimasRespuestas(instantanea)
            settingsStore.setTurno(turnoActual)
        }
    }

    fun next() {
        if (!_state.value.answered) return
        mostrarSiguiente()
    }

    /**
     * Recorrer el catálogo entero en cada respuesta parece caro, pero son unos
     * miles de sumas: menos de un milisegundo, y a cambio la barra se mueve en
     * el momento en que se acierta, que es cuando el avance significa algo.
     */
    private fun recalcularNivel() {
        val niveles = progresoNivel.porNivel(catalog, cards)
        _state.value = _state.value.copy(
            niveles = niveles,
            nivelAlcanzado = progresoNivel.nivelAlcanzado(niveles)
        )
    }

    fun aplicarNumeroDeOpciones(valor: Int) {
        optionCount = valor
    }

    private companion object {
        /** Respuestas que se tienen en cuenta para medir cómo va la cosa. */
        const val VENTANA = 50
        const val MINIMO_PARA_AJUSTAR = 15
    }
}
