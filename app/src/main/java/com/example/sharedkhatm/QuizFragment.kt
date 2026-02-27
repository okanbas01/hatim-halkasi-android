package com.example.sharedkhatm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bilgi Yarışması: Giriş ekranı Compose (responsive, production).
 * Oyun/oyun bitti XML. State ViewModel + SharedPreferences.
 */
class QuizFragment : Fragment(R.layout.fragment_quiz) {

    private val welcomeViewModel: QuizWelcomeViewModel by viewModels()

    private var currentScore = 0
    private var questionCount = 0
    private var usedIds = emptySet<Int>()
    private var currentQuestion: QuizQuestion? = null
    private var selectedIndex: Int = -1
    private var answered = false
    private var gameOver = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var quizWelcomeCompose: ComposeView
    private lateinit var quizGameScroll: View
    private lateinit var layoutQuizGame: View
    private lateinit var layoutQuizOver: View
    private lateinit var txtQuizScore: TextView
    private lateinit var txtQuizQuestionNum: TextView
    private lateinit var txtQuizQuestion: TextView
    private lateinit var option0: TextView
    private lateinit var option1: TextView
    private lateinit var option2: TextView
    private lateinit var option3: TextView
    private lateinit var btnQuizConfirm: TextView
    private lateinit var txtQuizFinalScore: TextView
    private lateinit var btnQuizRestart: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        view.findViewById<View>(R.id.btnQuizBack).setOnClickListener { parentFragmentManager.popBackStack() }

        quizWelcomeCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
            setContent {
                QuizTheme {
                    QuizWelcomeScreen(
                        viewModel = welcomeViewModel,
                        onStartClick = { startNewGame() },
                        onContinueClick = { continueGame() },
                        onRestartClick = { startNewGame() }
                    )
                }
            }
        }

        btnQuizConfirm.setOnClickListener { onConfirm() }
        btnQuizRestart.setOnClickListener { startNewGame() }

        val options = listOf(option0, option1, option2, option3)
        options.forEachIndexed { index, tv ->
            tv.setOnClickListener { if (!answered) selectOption(index) }
        }

        refreshStartScreen(isGameOver = false)
    }

    private fun bindViews(view: View) {
        quizWelcomeCompose = view.findViewById(R.id.quizWelcomeCompose)
        quizGameScroll = view.findViewById(R.id.quizGameScroll)
        layoutQuizGame = view.findViewById(R.id.layoutQuizGame)
        layoutQuizOver = view.findViewById(R.id.layoutQuizOver)
        txtQuizScore = view.findViewById(R.id.txtQuizScore)
        txtQuizQuestionNum = view.findViewById(R.id.txtQuizQuestionNum)
        txtQuizQuestion = view.findViewById(R.id.txtQuizQuestion)
        option0 = view.findViewById(R.id.option0)
        option1 = view.findViewById(R.id.option1)
        option2 = view.findViewById(R.id.option2)
        option3 = view.findViewById(R.id.option3)
        btnQuizConfirm = view.findViewById(R.id.btnQuizConfirm)
        txtQuizFinalScore = view.findViewById(R.id.txtQuizFinalScore)
        btnQuizRestart = view.findViewById(R.id.btnQuizRestart)
    }

    private fun refreshStartScreen(isGameOver: Boolean) {
        val ctx = requireContext()
        val high = QuizStorage.getHighScore(ctx)
        val last = QuizStorage.getLastScore(ctx)
        val hasContinue = QuizStorage.loadContinueState(ctx) != null
        welcomeViewModel.updateState(isGameOver, high, last, hasContinue)
        quizWelcomeCompose.visibility = View.VISIBLE
        quizGameScroll.visibility = View.GONE
    }

    private fun startNewGame() {
        QuizStorage.clearContinueState(requireContext())
        currentScore = 0
        questionCount = 0
        usedIds = emptySet<Int>()
        gameOver = false
        quizWelcomeCompose.visibility = View.GONE
        quizGameScroll.visibility = View.VISIBLE
        layoutQuizOver.visibility = View.GONE
        layoutQuizGame.visibility = View.VISIBLE
        loadNextQuestion()
    }

    private fun continueGame() {
        val ctx = requireContext()
        val state = QuizStorage.loadContinueState(ctx) ?: return
        currentScore = state.first
        usedIds = state.second
        questionCount = usedIds.size
        gameOver = false
        quizWelcomeCompose.visibility = View.GONE
        quizGameScroll.visibility = View.VISIBLE
        layoutQuizOver.visibility = View.GONE
        layoutQuizGame.visibility = View.VISIBLE
        val q = QuizStorage.getQuestionById(ctx, state.third)
        if (q != null) {
            currentQuestion = q
            showQuestion(q)
        } else {
            loadNextQuestion()
        }
    }

    private fun loadNextQuestion() {
        scope.launch {
            val q = withContext(Dispatchers.Default) {
                QuizStorage.pickRandomQuestion(requireContext(), usedIds)
            }
            if (q == null) {
                showStartScreen()
                return@launch
            }
            currentQuestion = q
            usedIds = usedIds + q.id
            questionCount = usedIds.size
            showQuestion(q)
        }
    }

    private fun showQuestion(q: QuizQuestion) {
        answered = false
        selectedIndex = -1
        btnQuizConfirm.isEnabled = false
        resetOptionBackgrounds()
        txtQuizQuestion.text = q.soru
        txtQuizScore.text = "Puan: $currentScore"
        txtQuizQuestionNum.text = "$questionCount/∞"
        val options = listOf(option0, option1, option2, option3)
        q.siklar.forEachIndexed { i, text -> options[i].text = text }
    }

    private fun resetOptionBackgrounds() {
        val ctx = requireContext()
        val options = listOf(option0, option1, option2, option3)
        options.forEach { tv ->
            tv.setBackgroundResource(R.drawable.bg_quiz_option)
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_dark))
        }
    }

    private fun selectOption(index: Int) {
        selectedIndex = index
        val ctx = requireContext()
        val options = listOf(option0, option1, option2, option3)
        options.forEachIndexed { i, tv ->
            tv.setBackgroundResource(R.drawable.bg_quiz_option)
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_dark))
        }
        options[index].setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_green))
        options[index].setTextColor(ContextCompat.getColor(ctx, R.color.white))
        btnQuizConfirm.isEnabled = true
    }

    private fun onConfirm() {
        val q = currentQuestion ?: return
        if (answered) return
        answered = true
        btnQuizConfirm.isEnabled = false
        val options = listOf(option0, option1, option2, option3)
        val ctx = requireContext()
        val green = ContextCompat.getColor(ctx, R.color.primary_green)
        val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
        val white = ContextCompat.getColor(ctx, R.color.white)
        val correctIndex = q.dogruIndex
        if (selectedIndex == correctIndex) {
            options[selectedIndex].setBackgroundColor(green)
            options[selectedIndex].setTextColor(white)
            currentScore += 1
            val high = QuizStorage.getHighScore(ctx)
            if (currentScore > high) QuizStorage.saveHighScore(ctx, currentScore)
            txtQuizScore.text = "Puan: $currentScore"
            mainHandler.postDelayed({ loadNextQuestion() }, 600)
        } else {
            options[selectedIndex].setBackgroundColor(red)
            options[selectedIndex].setTextColor(white)
            options[correctIndex].setBackgroundColor(green)
            options[correctIndex].setTextColor(white)
            gameOver = true
            QuizStorage.saveLastScore(ctx, currentScore)
            QuizStorage.clearContinueState(ctx)
            mainHandler.postDelayed({ showGameOver() }, 1200)
        }
    }

    private fun showGameOver() {
        layoutQuizGame.visibility = View.GONE
        layoutQuizOver.visibility = View.GONE
        refreshStartScreen(isGameOver = true)
    }

    private fun showStartScreen() {
        layoutQuizGame.visibility = View.GONE
        layoutQuizOver.visibility = View.GONE
        refreshStartScreen(isGameOver = true)
    }

    override fun onPause() {
        super.onPause()
        if (!gameOver && currentQuestion != null && quizGameScroll.visibility == View.VISIBLE && layoutQuizGame.visibility == View.VISIBLE) {
            QuizStorage.saveContinueState(requireContext(), currentScore, usedIds, currentQuestion!!.id)
        }
    }
}

@Composable
private fun QuizTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = Typography()
    ) {
        content()
    }
}
