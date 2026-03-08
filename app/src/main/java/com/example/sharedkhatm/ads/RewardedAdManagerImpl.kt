package com.example.sharedkhatm.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.sharedkhatm.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val TAG = "RewardedAdManager"
private const val SHOW_TIMEOUT_MS = 3000L

/**
 * Destekçi rewarded: izlenince SupportAdTracker.increment(). Reklamsızlık ödülü yok.
 * Preload arka planda. Show öncesi: activity null, isLoaded, 3sn timeout. Retry loop yok.
 */
class RewardedAdManagerImpl(
    private val context: Context,
    private val supportAdTracker: SupportAdTracker
) : RewardedAdManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rewardedAd: RewardedAd? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adUnitId: String
        get() = context.getString(R.string.admob_rewarded_id)

    override fun preloadRewarded() {
        AdPreferences.init(context)
        if (AdPreferences.isPremium()) return
        val ctx = context.applicationContext
        val unitId = adUnitId
        val managerRef = WeakReference(this)
        scope.launch {
            try {
                RewardedAd.load(
                    ctx,
                    unitId,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            managerRef.get()?.rewardedAd = ad
                            try { Log.d(TAG, "Rewarded Loaded") } catch (_: Exception) { }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            managerRef.get()?.rewardedAd = null
                            try { Log.d(TAG, "Rewarded Failed: ${error.message}") } catch (_: Exception) { }
                        }
                    }
                )
            } catch (_: Exception) { }
        }
    }

    override fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismissed()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            try { Log.d(TAG, "Rewarded not ready, skip (no force show)") } catch (_: Exception) { }
            preloadRewarded()
            onDismissed()
            return
        }
        rewardedAd = null
        var callbackFired = false
        fun safeDismiss() {
            if (callbackFired) return
            callbackFired = true
            preloadRewarded()
            onDismissed()
        }
        val timeoutRunnable = Runnable { safeDismiss() }
        mainHandler.postDelayed(timeoutRunnable, SHOW_TIMEOUT_MS)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                mainHandler.removeCallbacks(timeoutRunnable)
                safeDismiss()
            }
            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                try { Log.d(TAG, "Rewarded show failed: ${e.message}") } catch (_: Exception) { }
                mainHandler.removeCallbacks(timeoutRunnable)
                safeDismiss()
            }
        }
        AdReportingLog.logShow(AdReportingLog.Format.REWARDED, activity::class.java.simpleName, if (AdPreferences.isPremium()) "premium" else "default")
        ad.show(activity) { _ ->
            try { Log.d(TAG, "User earned reward") } catch (_: Exception) { }
            supportAdTracker.increment()
            onRewarded()
        }
    }
}
