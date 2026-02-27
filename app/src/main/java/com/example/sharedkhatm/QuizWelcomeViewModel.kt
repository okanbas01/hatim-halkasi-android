package com.example.sharedkhatm

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tek kaynak: giriş ekranı UI state.
 * Animasyon/LaunchedEffect/ağ yok; sadece state tutar.
 */
data class QuizWelcomeUiState(
    val isGameOver: Boolean = false,
    val highScore: Int = 0,
    val lastScore: Int = -1,
    val hasContinueState: Boolean = false,
    val title: String = "İslami Bilgi Yarışmasına Hoşgeldiniz",
    val primaryButtonText: String = "Başla"
) {
    companion object {
        fun create(
            isGameOver: Boolean,
            highScore: Int,
            lastScore: Int,
            hasContinueState: Boolean
        ): QuizWelcomeUiState {
            val title = if (isGameOver) "Yeniden Başlamaya Hazır Mısın?" else "İslami Bilgi Yarışmasına Hoşgeldiniz"
            val primaryButtonText = if (isGameOver) "Yeniden Başla" else "Başla"
            return QuizWelcomeUiState(
                isGameOver = isGameOver,
                highScore = highScore,
                lastScore = lastScore,
                hasContinueState = hasContinueState,
                title = title,
                primaryButtonText = primaryButtonText
            )
        }
    }
}

class QuizWelcomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QuizWelcomeUiState())
    val uiState: StateFlow<QuizWelcomeUiState> = _uiState.asStateFlow()

    fun updateState(isGameOver: Boolean, highScore: Int, lastScore: Int, hasContinueState: Boolean) {
        _uiState.value = QuizWelcomeUiState.create(
            isGameOver = isGameOver,
            highScore = highScore,
            lastScore = lastScore,
            hasContinueState = hasContinueState
        )
    }
}
