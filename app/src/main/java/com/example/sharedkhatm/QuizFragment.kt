package com.example.sharedkhatm

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import android.widget.Toast
import com.example.sharedkhatm.ads.AdPreferences
import com.example.sharedkhatm.ads.AdViewModel
import com.example.sharedkhatm.ads.Screen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bilgi Yarışması: 10 soruluk quiz, yanlışta reset yok. Sonuç ekranında yüzde, performans seviyesi, Tekrar Dene / Ana Menü.
 * State: ViewModel (sealed Loading / Question / Result). Rotation-safe.
 */
class QuizFragment : Fragment(R.layout.fragment_quiz) {

    private val adViewModel: AdViewModel by activityViewModels { AdViewModel.Factory(requireActivity().application) }
    private val welcomeViewModel: QuizWelcomeViewModel by viewModels()
    private val quizViewModel: QuizViewModel by viewModels()

    companion object {
        const val TOTAL_QUESTIONS = 10
    }

    private var selectedIndex: Int = -1
    private var answered = false

    private lateinit var quizWelcomeCompose: ComposeView
    private lateinit var quizGameScroll: View
    private lateinit var layoutQuizGame: View
    private lateinit var layoutQuizResult: View
    private lateinit var txtQuizScore: TextView
    private lateinit var txtQuizQuestionNum: TextView
    private lateinit var txtQuizQuestion: TextView
    private lateinit var option0: TextView
    private lateinit var option1: TextView
    private lateinit var option2: TextView
    private lateinit var option3: TextView
    private lateinit var btnQuizConfirm: TextView
    private lateinit var txtQuizResultPercent: TextView
    private lateinit var txtQuizResultCorrect: TextView
    private lateinit var txtQuizResultWrong: TextView
    private lateinit var txtQuizResultBadge: TextView
    private lateinit var txtQuizHighScore: TextView
    private lateinit var btnQuizTryAgain: TextView
    private lateinit var btnQuizMainMenu: TextView
    private var quizAdBannerContainer: FrameLayout? = null

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
                        onContinueClick = { startNewGame() },
                        onRestartClick = { startNewGame() }
                    )
                }
            }
        }

        btnQuizConfirm.setOnClickListener { onConfirm() }
        btnQuizTryAgain.setOnClickListener { startNewGame() }
        btnQuizMainMenu.setOnClickListener { showMainMenu() }
        view.findViewById<TextView>(R.id.btnQuizExtraPack).setOnClickListener { showRewardedForExtraPack() }

        val options = listOf(option0, option1, option2, option3)
        options.forEachIndexed { index, tv ->
            tv.setOnClickListener { if (!answered) selectOption(index) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                quizViewModel.state.collectLatest { state ->
                    when (state) {
                        is QuizUiState.Loading -> {
                            quizWelcomeCompose.visibility = View.VISIBLE
                            quizGameScroll.visibility = View.GONE
                        }
                        is QuizUiState.Question -> showQuestionState(state)
                        is QuizUiState.Result -> {
                            showResultState(state)
                            (requireActivity().application as? MyApplication)?.interstitialManager?.maybeShowInterstitial(requireActivity(), com.example.sharedkhatm.ads.AdScreenContext.NORMAL)
                        }
                    }
                }
            }
        }

        refreshStartScreen(isGameOver = false)
    }

    private fun bindViews(view: View) {
        quizWelcomeCompose = view.findViewById(R.id.quizWelcomeCompose)
        quizGameScroll = view.findViewById(R.id.quizGameScroll)
        layoutQuizGame = view.findViewById(R.id.layoutQuizGame)
        layoutQuizResult = view.findViewById(R.id.layoutQuizResult)
        txtQuizScore = view.findViewById(R.id.txtQuizScore)
        txtQuizQuestionNum = view.findViewById(R.id.txtQuizQuestionNum)
        txtQuizQuestion = view.findViewById(R.id.txtQuizQuestion)
        option0 = view.findViewById(R.id.option0)
        option1 = view.findViewById(R.id.option1)
        option2 = view.findViewById(R.id.option2)
        option3 = view.findViewById(R.id.option3)
        btnQuizConfirm = view.findViewById(R.id.btnQuizConfirm)
        txtQuizResultPercent = view.findViewById(R.id.txtQuizResultPercent)
        txtQuizResultCorrect = view.findViewById(R.id.txtQuizResultCorrect)
        txtQuizResultWrong = view.findViewById(R.id.txtQuizResultWrong)
        txtQuizResultBadge = view.findViewById(R.id.txtQuizResultBadge)
        txtQuizHighScore = view.findViewById(R.id.txtQuizHighScore)
        btnQuizTryAgain = view.findViewById(R.id.btnQuizTryAgain)
        btnQuizMainMenu = view.findViewById(R.id.btnQuizMainMenu)
        quizAdBannerContainer = view.findViewById(R.id.quizAdBanner)
    }

    private fun refreshStartScreen(isGameOver: Boolean) {
        val ctx = requireContext()
        val high = QuizStorage.getHighScore(ctx)
        val last = QuizStorage.getLastScore(ctx)
        welcomeViewModel.updateState(isGameOver, high, last, hasContinueState = false)
    }

    private fun startNewGame() {
        QuizStorage.clearContinueState(requireContext())
        quizWelcomeCompose.visibility = View.GONE
        quizGameScroll.visibility = View.VISIBLE
        layoutQuizResult.visibility = View.GONE
        layoutQuizGame.visibility = View.VISIBLE

        // Banner: Soru cevaplama alanında, Başla sonrası
        val adContainer = quizAdBannerContainer
        if (adContainer != null) {
            if (adViewModel.shouldShowAds(requireContext())) {
                adContainer.visibility = View.VISIBLE
                adViewModel.loadBanner(requireActivity(), adContainer, Screen.QUIZ)
            } else {
                adContainer.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            val questions = withContext(Dispatchers.Default) {
                QuizStorage.pickRandomQuestions(requireContext(), TOTAL_QUESTIONS)
            }
            quizViewModel.startQuiz(questions)
        }
    }

    private fun showQuestionState(state: QuizUiState.Question) {
        layoutQuizGame.visibility = View.VISIBLE
        layoutQuizResult.visibility = View.GONE
        answered = false
        selectedIndex = -1
        btnQuizConfirm.isEnabled = false
        resetOptionBackgrounds()

        val q = state.question
        txtQuizQuestion.text = q.soru
        txtQuizScore.text = "Doğru: ${state.correctCountSoFar}"
        txtQuizQuestionNum.text = "${state.questionIndex + 1}/${state.totalQuestions}"
        val options = listOf(option0, option1, option2, option3)
        q.siklar.forEachIndexed { i, text -> options[i].text = text }
    }

    private fun resetOptionBackgrounds() {
        val ctx = requireContext()
        val options = listOf(option0, option1, option2, option3)
        options.forEach { tv ->
            tv.setBackgroundResource(R.drawable.bg_quiz_option)
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_dark))
            tv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(0).start()
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
        val state = quizViewModel.state.value
        if (state !is QuizUiState.Question || answered) return
        answered = true
        btnQuizConfirm.isEnabled = false

        val q = state.question
        val options = listOf(option0, option1, option2, option3)
        val ctx = requireContext()
        val green = ContextCompat.getColor(ctx, R.color.primary_green)
        val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
        val white = ContextCompat.getColor(ctx, R.color.white)
        val correctIndex = q.dogruIndex

        options[selectedIndex].setBackgroundColor(if (selectedIndex == correctIndex) green else red)
        options[selectedIndex].setTextColor(white)
        options[correctIndex].setBackgroundColor(green)
        options[correctIndex].setTextColor(white)

        val isCorrect = selectedIndex == correctIndex
        if (isCorrect) {
            options[selectedIndex].animate()
                .scaleX(1.08f).scaleY(1.08f)
                .setDuration(150)
                .withEndAction {
                    options[selectedIndex].animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }

        val highScore = QuizStorage.getHighScore(ctx)
        lifecycleScope.launch {
            delay(700)
            quizViewModel.submitAnswer(selectedIndex, state.totalQuestions, highScore)
        }
    }

    private fun showResultState(state: QuizUiState.Result) {
        layoutQuizGame.visibility = View.GONE
        layoutQuizResult.visibility = View.VISIBLE
        quizAdBannerContainer?.let { adViewModel.destroyBanner(it); it.visibility = View.GONE }

        val ctx = requireContext()
        if (state.correctCount > QuizStorage.getHighScore(ctx)) {
            QuizStorage.saveHighScore(ctx, state.correctCount)
        }
        QuizStorage.saveLastScore(ctx, state.correctCount)

        txtQuizResultPercent.text = "${state.percentage}%"
        val percentColor = when {
            state.percentage >= 90 -> ContextCompat.getColor(ctx, R.color.quiz_score_high)
            state.percentage >= 50 -> ContextCompat.getColor(ctx, R.color.quiz_score_mid)
            else -> ContextCompat.getColor(ctx, R.color.quiz_score_low)
        }
        txtQuizResultPercent.setTextColor(percentColor)

        txtQuizResultCorrect.text = "Doğru: ${state.correctCount}"
        txtQuizResultWrong.text = "Yanlış: ${state.wrongCount}"
        txtQuizResultBadge.text = state.performanceLevel.displayText()
        txtQuizHighScore.text = "En Yüksek Skorun: ${state.highScore}/$TOTAL_QUESTIONS"

        layoutQuizResult.alpha = 0f
        layoutQuizResult.scaleX = 0.92f
        layoutQuizResult.scaleY = 0.92f
        layoutQuizResult.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(450)
            .start()
    }

    private fun showMainMenu() {
        quizViewModel.resetToLoading()
        layoutQuizGame.visibility = View.GONE
        layoutQuizResult.visibility = View.GONE
        quizAdBannerContainer?.let { adViewModel.destroyBanner(it); it.visibility = View.GONE }
        refreshStartScreen(isGameOver = true)
        quizWelcomeCompose.visibility = View.VISIBLE
        quizGameScroll.visibility = View.GONE
    }

    override fun onDestroyView() {
        quizAdBannerContainer?.let { adViewModel.destroyBanner(it) }
        quizAdBannerContainer = null
        super.onDestroyView()
    }

    /** Rewarded: Ekstra soru paketi açma (isteğe bağlı, iptal edilebilir). */
    private fun showRewardedForExtraPack() {
        AdPreferences.init(requireContext())
        if (!AdPreferences.shouldShowAds()) return
        val act = activity ?: return
        if (act.isFinishing || act.isDestroyed) return
        adViewModel.showRewarded(act,
            onRewarded = {
                requireContext().getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("extra_pack_unlocked", true).apply()
                if (isAdded) Toast.makeText(requireContext(), "Ekstra soru paketi açıldı.", Toast.LENGTH_SHORT).show()
            },
            onDismissed = { }
        )
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
