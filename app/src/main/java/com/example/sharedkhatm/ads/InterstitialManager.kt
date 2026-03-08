package com.example.sharedkhatm.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.sharedkhatm.BuildConfig
import com.example.sharedkhatm.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val TAG = "InterstitialManager"
private const val PREFS = "interstitial_prefs"
private const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
private const val KEY_INTERACTION_COUNT = "interaction_count"
private const val KEY_LAST_SHOW_DATE = "last_show_date"
private const val KEY_TODAY_SHOW_COUNT = "today_show_count"
private const val FIRST_LAUNCHES_NO_AD = 3

/**
 * Interstitial: Sadece sure tamamlandıktan veya yarışma sonucu ekranında.
 * Frekans: 4 etkileşimde 1, günde max 3. İlk 3 uygulama açılışında ve cold start'ta gösterilmez.
 */
class InterstitialManager(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var interstitialAd: InterstitialAd? = null

    private val adUnitId: String
        get() = context.getString(R.string.admob_interstitial_id)

    /** Uygulama açılış sayacı. Production'da devre dışı; sadece debug'da çalışır. */
    fun recordAppLaunch() {
        if (!BuildConfig.DEBUG) return
        scope.launch(Dispatchers.IO) {
            val count = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)
            prefs.edit().putInt(KEY_APP_LAUNCH_COUNT, count + 1).apply()
        }
    }

    /** Production'da devre dışı; sadece Banner + Rewarded aktif. */
    fun preload() {
        if (!BuildConfig.DEBUG) return
        if (AdPreferences.isPremium()) return
        val ctx = context.applicationContext
        val unitId = adUnitId
        val managerRef = java.lang.ref.WeakReference(this)
        scope.launch {
            try {
                InterstitialAd.load(
                    ctx,
                    unitId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            managerRef.get()?.interstitialAd = ad
                            try { Log.d(TAG, "Interstitial loaded") } catch (_: Exception) { }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            managerRef.get()?.interstitialAd = null
                            try { Log.d(TAG, "Interstitial failed: ${error.message}") } catch (_: Exception) { }
                        }
                    }
                )
            } catch (_: Exception) { }
        }
    }

    /**
     * Sure tamamlandı veya yarışma sonucu ekranında çağrılır.
     * Production'da devre dışı; sadece debug'da gösterilir. Caller onDismissed ile devam eder.
     */
    fun maybeShowInterstitial(
        activity: Activity,
        screenContext: AdScreenContext = AdScreenContext.NORMAL,
        onDismissed: (() -> Unit)? = null
    ) {
        if (!BuildConfig.DEBUG) {
            onDismissed?.invoke()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onDismissed?.invoke()
            return
        }
        if (screenContext == AdScreenContext.SENSITIVE) {
            onDismissed?.invoke()
            return
        }
        if (AdPreferences.isPremium()) {
            onDismissed?.invoke()
            return
        }
        val appLaunches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)
        if (appLaunches < FIRST_LAUNCHES_NO_AD) {
            onDismissed?.invoke()
            return
        }
        val prevCount = prefs.getInt(KEY_INTERACTION_COUNT, 0)
        val interactionCount = prevCount + 1
        prefs.edit().putInt(KEY_INTERACTION_COUNT, interactionCount).apply()
        if (interactionCount % AdRemoteConfig.interstitialFrequency() != 0) {
            onDismissed?.invoke()
            return
        }
        val todayKey = todayKey()
        val lastDate = prefs.getString(KEY_LAST_SHOW_DATE, "")
        val todayCount = if (lastDate == todayKey) prefs.getInt(KEY_TODAY_SHOW_COUNT, 0) else 0
        if (todayCount >= AdRemoteConfig.interstitialMaxPerDay()) {
            onDismissed?.invoke()
            return
        }
        val ad = interstitialAd ?: run {
            preload()
            onDismissed?.invoke()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                val newCount = todayCount + 1
                prefs.edit()
                    .putString(KEY_LAST_SHOW_DATE, todayKey)
                    .putInt(KEY_TODAY_SHOW_COUNT, newCount)
                    .apply()
                preload()
                onDismissed?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                try { Log.d(TAG, "Interstitial show failed: ${e.message}") } catch (_: Exception) { }
                preload()
                onDismissed?.invoke()
            }
        }
        AdReportingLog.logShow(AdReportingLog.Format.INTERSTITIAL, activity::class.java.simpleName, userSegment())
        ad.show(activity)
    }

    private fun userSegment(): String = if (AdPreferences.isPremium()) "premium" else "default"

    private fun todayKey(): String {
        val c = Calendar.getInstance(Locale.getDefault())
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    fun onDestroy() {
        interstitialAd = null
    }
}
