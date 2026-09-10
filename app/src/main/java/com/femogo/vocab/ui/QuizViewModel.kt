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
    val position: Int = 0,
    val total: Int = 0,
    val correctSoFar: Int = 0,
    val finished: Boolean = false,
    val catalogSize: Int = 0,
    val dueCount: Int = 0
) {
    val answered: Boolean get() = chosenIndex != null
    val wasCorrect: Boolean get() = chosenIndex != null && chosenIndex == question?.correctIndex
}

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as VocabApplication).repository
    private val settingsStore = (app as VocabApplication).settings

    private val leitner = Leitner()
    private val quizBuilder = QuizBuilder()

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var catalog: List<Word> = emptyList()
    private var cards: MutableMap<Int, Card> = mutableMapOf()
    private var queue: List<Word> = emptyList()
    private var index = 0
    private var optionCount = 4
    private var directionMode = DirectionMode.PROGRESSIVE

    init { startSession() }

    fun startSession() {
        viewModelScope.launch {
            _state.value = QuizUiState(loading = true)

            repo.ensureSeeded()
            catalog = repo.catalog()
            cards = repo.cards().toMutableMap()

            val settings = settingsStore.flow.first()
            optionCount = settings.optionCount
            directionMode = settings.directionMode
            val now = System.currentTimeMillis()
            val scheduler = Scheduler(leitner, newPerDay = settings.newPerDay)

            queue = scheduler.buildSession(
                catalog = catalog,
                cards = cards,
                now = now,
                size = settings.sessionSize,
                newIntroducedToday = repo.introducedToday(now)
            )
            index = 0

            _state.value = QuizUiState(
                loading = false,
                total = queue.size,
                catalogSize = catalog.size,
                dueCount = scheduler.dueCount(cards, now)
            )
            showCurrent()
        }
    }

    private fun showCurrent() {
        val word = queue.getOrNull(index)
        if (word == null) {
            _state.value = _state.value.copy(question = null, chosenIndex = null, finished = index > 0)
            return
        }
        val card = cards[word.rank] ?: leitner.newCard(word.rank, System.currentTimeMillis())
        _state.value = _state.value.copy(
            question = quizBuilder.build(
                target = word,
                direction = leitner.directionFor(card, directionMode),
                pool = catalog,
                optionCount = optionCount
            ),
            chosenIndex = null,
            position = index + 1
        )
    }

    fun answer(chosen: Int) {
        val current = _state.value
        // Ignorar pulsaciones repetidas: la respuesta ya se contabilizó.
        if (current.answered || current.question == null) return

        val question = current.question
        val correct = chosen == question.correctIndex
        val now = System.currentTimeMillis()
        val card = cards[question.word.rank] ?: leitner.newCard(question.word.rank, now)
        val updated = leitner.answer(card, correct, now)
        cards[question.word.rank] = updated

        _state.value = current.copy(
            chosenIndex = chosen,
            correctSoFar = current.correctSoFar + if (correct) 1 else 0
        )
        viewModelScope.launch { repo.save(updated, now) }
    }

    fun next() {
        if (!_state.value.answered) return
        index++
        showCurrent()
    }
}
