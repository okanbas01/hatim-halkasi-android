package com.example.sharedkhatm.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.sharedkhatm.BuildConfig
import com.example.sharedkhatm.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val TAG = "AppOpenManager"
private const val PREFS = "app_open_prefs"
private const val KEY_LAST_OPEN_DATE = "last_open_date"
private const val KEY_ACTIVE_DAYS = "active_days"
private const val KEY_APP_OPEN_SHOWN_DATE = "app_open_shown_date"
private const val KEY_APP_OPEN_COUNT_TODAY = "app_open_count_today"
private const val DELAY_MS = 2000L

/**
 * App Open: Günde max 1, uygulama açıldıktan 2 sn sonra, sadece 3+ gün aktif kullanıcıya.
 * Cold start'ı bozmayacak şekilde preload.
 */
class AppOpenManager(private val application: Application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appOpenAd: AppOpenAd? = null

    private val adUnitId: String
        get() = application.getString(R.string.admob_app_open_id)

    /** Production'da devre dışı; sadece Banner + Rewarded aktif. */
    fun preload() {
        if (!BuildConfig.DEBUG) return
        if (AdPreferences.isPremium()) return
        val ctx = application.applicationContext
        val unitId = adUnitId
        val managerRef = java.lang.ref.WeakReference(this)
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                AppOpenAd.load(
                    ctx,
                    unitId,
                    AdRequest.Builder().build(),
                    AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            managerRef.get()?.appOpenAd = ad
                            try { Log.d(TAG, "AppOpen loaded") } catch (_: Exception) { }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            managerRef.get()?.appOpenAd = null
                            try { Log.d(TAG, "AppOpen failed: ${error.message}") } catch (_: Exception) { }
                        }
                    }
                )
            } catch (_: Exception) { }
        }
    }

    /**
     * HomeActivity'den çağrılır. Production'da devre dışı; sadece debug'da çalışır.
     */
    fun maybeShowAfterDelay(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (AdPreferences.isPremium()) return
        recordOpenDay()
        val activeDays = prefs.getInt(KEY_ACTIVE_DAYS, 0)
        if (activeDays < AdRemoteConfig.appOpenMinActiveDays()) return
        val todayKey = todayKey()
        val lastShownDate = prefs.getString(KEY_APP_OPEN_SHOWN_DATE, "")
        val countToday = if (lastShownDate == todayKey) prefs.getInt(KEY_APP_OPEN_COUNT_TODAY, 0) else 0
        if (countToday >= AdRemoteConfig.appOpenMaxPerDay()) return
        mainHandler.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            tryShow(activity, todayKey, countToday)
        }, DELAY_MS)
    }

    private fun recordOpenDay() {
        val today = todayKey()
        val last = prefs.getString(KEY_LAST_OPEN_DATE, "")
        if (last != today) {
            val days = prefs.getInt(KEY_ACTIVE_DAYS, 0)
            prefs.edit()
                .putString(KEY_LAST_OPEN_DATE, today)
                .putInt(KEY_ACTIVE_DAYS, days + 1)
                .apply()
        }
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance(Locale.getDefault())
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun tryShow(activity: Activity, todayKey: String, countToday: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val ad = appOpenAd ?: return
        appOpenAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                prefs.edit()
                    .putString(KEY_APP_OPEN_SHOWN_DATE, todayKey)
                    .putInt(KEY_APP_OPEN_COUNT_TODAY, countToday + 1)
                    .apply()
                preload()
            }
            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                try { Log.d(TAG, "AppOpen show failed: ${e.message}") } catch (_: Exception) { }
                preload()
            }
        }
        AdReportingLog.logShow(AdReportingLog.Format.APP_OPEN, "AppOpen", if (AdPreferences.isPremium()) "premium" else "default")
        ad.show(activity)
    }

    fun onDestroy() {
        appOpenAd = null
    }
}
