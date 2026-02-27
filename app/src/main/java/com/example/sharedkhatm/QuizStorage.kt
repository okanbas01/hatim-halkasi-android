package com.example.sharedkhatm

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * Bilgi yarışması: Sorular assets JSON'dan lazy yüklenir, state SharedPreferences ile O(1) kayıt.
 * Aynı oyun içinde tekrar etmeyen random seçim (usedIds set).
 */
object QuizStorage {

    private const val PREFS = "QuizPrefs"
    private const val KEY_HIGH_SCORE = "high_score"
    private const val KEY_LAST_SCORE = "last_score"
    private const val KEY_CURRENT_SCORE = "current_score"
    private const val KEY_USED_IDS = "used_ids"
    private const val KEY_CURRENT_QUESTION_ID = "current_question_id"

    private val gson = Gson()
    private val listType = object : TypeToken<ArrayList<QuizQuestion>>() {}.type

    @Volatile
    private var allQuestions: List<QuizQuestion>? = null

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tüm soruları assets'ten bir kez yükle (lazy). */
    fun loadQuestions(context: Context): List<QuizQuestion> {
        return allQuestions ?: synchronized(this) {
            allQuestions ?: run {
                context.assets.open("quiz_questions.json").use { stream ->
                    val list = gson.fromJson<ArrayList<QuizQuestion>>(
                        InputStreamReader(stream, Charsets.UTF_8),
                        listType
                    ) ?: emptyList()
                    allQuestions = list
                    list
                }
            }
        }
    }

    /** Kullanılmamış ID'lerden rastgele bir soru seç. Oyun içinde tekrar etmez. */
    fun pickRandomQuestion(context: Context, usedIds: Set<Int>): QuizQuestion? {
        val list = loadQuestions(context)
        val available = list.filter { it.id !in usedIds }
        return available.getOrNull(Random.nextInt(available.size))
    }

    fun getQuestionById(context: Context, id: Int): QuizQuestion? =
        loadQuestions(context).find { it.id == id }

    // --- State (O(1) kayıt) ---

    fun getHighScore(context: Context): Int =
        prefs(context).getInt(KEY_HIGH_SCORE, 0)

    fun getLastScore(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SCORE, 0)

    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    fun saveLastScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_LAST_SCORE, score).apply()
    }

    /** Devam için: mevcut puan, kullanılan ID'ler, şu anki soru ID. */
    fun saveContinueState(context: Context, currentScore: Int, usedIds: Set<Int>, currentQuestionId: Int) {
        prefs(context).edit()
            .putInt(KEY_CURRENT_SCORE, currentScore)
            .putString(KEY_USED_IDS, usedIds.joinToString(","))
            .putInt(KEY_CURRENT_QUESTION_ID, currentQuestionId)
            .apply()
    }

    fun loadContinueState(context: Context): Triple<Int, Set<Int>, Int>? {
        val p = prefs(context)
        val currentQuestionId = p.getInt(KEY_CURRENT_QUESTION_ID, -1)
        if (currentQuestionId < 0) return null
        val currentScore = p.getInt(KEY_CURRENT_SCORE, 0)
        val usedStr = p.getString(KEY_USED_IDS, "") ?: ""
        val usedIds = if (usedStr.isEmpty()) emptySet() else usedStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        return Triple(currentScore, usedIds, currentQuestionId)
    }

    /** Oyun bitti (yanlış cevap) veya kullanıcı yeni başladı; devam state temizle. */
    fun clearContinueState(context: Context) {
        prefs(context).edit()
            .remove(KEY_CURRENT_SCORE)
            .remove(KEY_USED_IDS)
            .remove(KEY_CURRENT_QUESTION_ID)
            .apply()
    }
}
