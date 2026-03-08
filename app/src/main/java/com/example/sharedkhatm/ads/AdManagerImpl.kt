package com.example.sharedkhatm.ads

import android.app.Activity
import com.example.sharedkhatm.R
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val TAG = "AdManager"

/**
 * Sadece banner. Ekran politikası BannerStateController ile. Rewarded → RewardedAdManager.
 */
class AdManagerImpl(
    private val context: Context,
    private val bannerStateController: BannerStateController
) : AdManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bannerAdView: WeakReference<AdView?>? = null

    override fun shouldShowAds(context: Context): Boolean {
        AdPreferences.init(context)
        return AdPreferences.shouldShowAds()
    }

    override fun loadBanner(activity: Activity, container: FrameLayout, screen: Screen) {
        if (com.example.sharedkhatm.BuildConfig.DEBUG) {
            try { Log.d("AdMob", "loadBanner called for $screen") } catch (_: Exception) { }
        }
        val shouldShow = bannerStateController.shouldShowBanner(screen)
        if (com.example.sharedkhatm.BuildConfig.DEBUG) {
            try { Log.d("AdMob", "shouldShowBanner: $shouldShow") } catch (_: Exception) { }
        }
        if (!shouldShow || !shouldShowAds(activity)) {
            container.visibility = android.view.View.GONE
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return
        container.visibility = android.view.View.VISIBLE
        container.minimumHeight = (50 * context.resources.displayMetrics.density).toInt()
        val containerRef = WeakReference(container)
        val activityRef = WeakReference(activity)
        val screenName = screen.name
        scope.launch {
            val act = activityRef.get() ?: return@launch
            if (act.isFinishing || act.isDestroyed) return@launch
            val adView = AdView(act).apply {
                setAdSize(adaptiveAdSize(act))
                adUnitId = context.getString(R.string.admob_banner_id)
            }
            adView.adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    if (com.example.sharedkhatm.BuildConfig.DEBUG) {
                        try { Log.d("AdMob", "Banner Loaded") } catch (_: Exception) { }
                    }
                    AdReportingLog.logShow(AdReportingLog.Format.BANNER, screenName, if (AdPreferences.isPremium()) "premium" else "default")
                    val c = containerRef.get() ?: return
                    val a = activityRef.get() ?: return
                    if (a.isFinishing || a.isDestroyed) return
                    c.visibility = android.view.View.VISIBLE
                    try {
                        c.removeAllViews()
                        c.addView(adView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    } catch (_: Exception) { }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (com.example.sharedkhatm.BuildConfig.DEBUG) {
                        try { Log.d("AdMob", "Banner Failed: ${error.message}") } catch (_: Exception) { }
                    }
                    logAdError("Banner", error)
                    try { containerRef.get()?.visibility = android.view.View.GONE } catch (_: Exception) { }
                }
            }
            bannerAdView = WeakReference(adView)
            if (com.example.sharedkhatm.BuildConfig.DEBUG) {
                try { Log.d("AdMob", "Banner loadAd called") } catch (_: Exception) { }
            }
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    override fun destroyBanner(container: FrameLayout?) {
        try {
            val adView = bannerAdView?.get()
            adView?.adListener = object : com.google.android.gms.ads.AdListener() {}
            container?.removeAllViews()
            adView?.destroy()
            bannerAdView = null
        } catch (_: Exception) { }
    }

    private fun adaptiveAdSize(activity: Activity): AdSize {
        val outMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(outMetrics)
        val density = outMetrics.density
        val widthPixels = outMetrics.widthPixels
        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    private fun logAdError(adType: String, error: LoadAdError) {
        try {
            Log.e(TAG, "$adType failed: domain=${error.domain} code=${error.code} message=${error.message}")
        } catch (_: Exception) { }
    }

    companion object {
        val TEST_DEVICE_IDS: List<String> = if (com.example.sharedkhatm.BuildConfig.DEBUG) {
            listOf(AdRequest.DEVICE_ID_EMULATOR)
        } else {
            emptyList()
        }
    }
}
