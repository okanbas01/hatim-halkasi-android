package com.example.sharedkhatm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quiz UI state. Single source of truth for game flow.
 * Rotation-safe: state held in ViewModel.
 */
sealed class QuizUiState {
    data object Loading : QuizUiState()
    data class Question(
        val question: QuizQuestion,
        val questionIndex: Int,
        val totalQuestions: Int,
        val correctCountSoFar: Int
    ) : QuizUiState()
    data class Result(
        val totalQuestions: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val percentage: Int,
        val performanceLevel: QuizPerformanceLevel,
        val highScore: Int
    ) : QuizUiState()
}

enum class QuizPerformanceLevel(val label: String, val emoji: String) {
    EXCELLENT("Mükemmel", "🏆"),
    VERY_GOOD("Çok İyi", "⭐"),
    GOOD("İyi", "👍"),
    TRY_AGAIN("Tekrar Denemelisin", "📘");

    fun displayText(): String = "$emoji $label"
}

private const val DEFAULT_TOTAL_QUESTIONS = 10

class QuizViewModel : ViewModel() {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var currentQuestions: List<QuizQuestion> = emptyList()
    private var currentIndex: Int = 0
    private var correctCount: Int = 0

    /**
     * Starts a new quiz with [questions]. Call from Fragment after loading via QuizStorage.pickRandomQuestions().
     */
    fun startQuiz(questions: List<QuizQuestion>) {
        if (questions.isEmpty()) {
            _state.value = QuizUiState.Loading
            return
        }
        val total = questions.size
        currentQuestions = questions
        currentIndex = 0
        correctCount = 0
        _state.value = QuizUiState.Question(
            question = questions[0],
            questionIndex = 0,
            totalQuestions = total,
            correctCountSoFar = 0
        )
    }

    /**
     * Submit selected answer. Advances to next question or emits Result when done.
     */
    fun submitAnswer(selectedIndex: Int, totalQuestions: Int, highScore: Int) {
        val state = _state.value
        if (state !is QuizUiState.Question) return
        val q = state.question
        val isCorrect = selectedIndex == q.dogruIndex
        val newCorrect = state.correctCountSoFar + if (isCorrect) 1 else 0
        correctCount = newCorrect

        val nextIndex = state.questionIndex + 1
        if (nextIndex >= totalQuestions) {
            val wrong = totalQuestions - newCorrect
            val percentage = if (totalQuestions > 0) (newCorrect * 100) / totalQuestions else 0
            val level = when {
                percentage >= 90 -> QuizPerformanceLevel.EXCELLENT
                percentage >= 70 -> QuizPerformanceLevel.VERY_GOOD
                percentage >= 50 -> QuizPerformanceLevel.GOOD
                else -> QuizPerformanceLevel.TRY_AGAIN
            }
            val newHighScore = maxOf(highScore, newCorrect)
            _state.value = QuizUiState.Result(
                totalQuestions = totalQuestions,
                correctCount = newCorrect,
                wrongCount = wrong,
                percentage = percentage,
                performanceLevel = level,
                highScore = newHighScore
            )
        } else {
            currentIndex = nextIndex
            _state.value = QuizUiState.Question(
                question = currentQuestions[nextIndex],
                questionIndex = nextIndex,
                totalQuestions = totalQuestions,
                correctCountSoFar = newCorrect
            )
        }
    }

    fun resetToLoading() {
        _state.value = QuizUiState.Loading
        currentQuestions = emptyList()
        currentIndex = 0
        correctCount = 0
    }
}
