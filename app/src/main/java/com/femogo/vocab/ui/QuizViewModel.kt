package com.femogo.vocab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.femogo.vocab.VocabApplication
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.DirectionMode
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
    val respondidas: Int = 0,
    val aciertos: Int = 0,
    val sinDiccionario: Boolean = false
) {
    val answered: Boolean get() = chosenIndex != null
    val wasCorrect: Boolean get() = chosenIndex != null && chosenIndex == question?.correctIndex
    val racha: Int get() = aciertos
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

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var catalog: List<Word> = emptyList()
    private val cards = mutableMapOf<Int, Card>()
    private var cola = ArrayDeque<Word>()
    private var optionCount = 4

    init { arrancar() }

    fun arrancar() {
        viewModelScope.launch {
            _state.value = QuizUiState(loading = true)
            repo.ensureSeeded()
            catalog = repo.catalog()
            cards.clear()
            cards.putAll(repo.cards())
            optionCount = settingsStore.flow.first().optionCount

            if (catalog.isEmpty()) {
                _state.value = QuizUiState(loading = false, sinDiccionario = true)
                return@launch
            }
            cola.clear()
            rellenar()
            _state.value = QuizUiState(loading = false)
            mostrarSiguiente()
        }
    }

    /** Se llama al recargar el diccionario, para no seguir preguntando lo viejo. */
    fun recargarCatalogo() {
        viewModelScope.launch {
            catalog = repo.catalog()
            cola.clear()
            if (catalog.isNotEmpty()) {
                rellenar()
                _state.value = _state.value.copy(sinDiccionario = false)
                mostrarSiguiente()
            }
        }
    }

    private fun rellenar() {
        val pendientes = cola.map { it.rank }.toSet()
        scheduler.buildQueue(catalog, cards, System.currentTimeMillis(), size = TAMAÑO_COLA)
            .filter { it.rank !in pendientes }
            .forEach { cola.addLast(it) }
    }

    private fun mostrarSiguiente() {
        if (cola.size <= RELLENAR_BAJO) rellenar()
        val word = cola.removeFirstOrNull()
        if (word == null) {
            _state.value = _state.value.copy(question = null, sinDiccionario = catalog.isEmpty())
            return
        }
        val card = cards[word.rank] ?: leitner.newCard(word.rank, System.currentTimeMillis())
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
        val ahora = System.currentTimeMillis()
        val card = cards[question.word.rank] ?: leitner.newCard(question.word.rank, ahora)
        val actualizada = leitner.answer(card, acierto, ahora)
        cards[question.word.rank] = actualizada

        _state.value = actual.copy(
            chosenIndex = chosen,
            respondidas = actual.respondidas + 1,
            aciertos = actual.aciertos + if (acierto) 1 else 0
        )
        viewModelScope.launch { repo.save(actualizada, ahora) }
    }

    fun next() {
        if (!_state.value.answered) return
        mostrarSiguiente()
    }

    fun aplicarNumeroDeOpciones(valor: Int) {
        optionCount = valor
    }

    private companion object {
        const val TAMAÑO_COLA = 40
        const val RELLENAR_BAJO = 8
    }
}
