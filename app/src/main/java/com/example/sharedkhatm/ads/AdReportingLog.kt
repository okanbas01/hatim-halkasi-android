package com.example.sharedkhatm.ads

import android.util.Log
import com.example.sharedkhatm.BuildConfig

/**
 * Reklam gösterim raporlama logu. Sadece DEBUG build'de yazılır.
 * Format, ekran adı ve kullanıcı segmenti loglanır.
 */
object AdReportingLog {

    private const val TAG = "AdReport"

    enum class Format { BANNER, INTERSTITIAL, REWARDED, APP_OPEN }

    /**
     * Her reklam gösteriminde çağrılır (debug only).
     * @param format Reklam formatı
     * @param screenName Ekran/akış adı (örn. "Dashboard", "ReadJuz", "QuizResult")
     * @param userSegment Kullanıcı segmenti (örn. "default", "guest", "signed_in")
     */
    @JvmStatic
    fun logShow(format: Format, screenName: String, userSegment: String = "default") {
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(TAG, "AdShow | format=$format | screen=$screenName | segment=$userSegment")
        } catch (_: Exception) { }
    }
}
