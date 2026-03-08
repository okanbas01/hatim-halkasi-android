package com.example.sharedkhatm.ads

import android.app.Activity
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import com.example.sharedkhatm.BuildConfig
import com.example.sharedkhatm.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TAG = "AdBannerCompose"

/**
 * Compose banner. Recomposition'da yeni AdView oluşturulmaz; remember + DisposableEffect.
 * Sadece BannerStateController.shouldShowBanner(screen) true ise render edilir.
 */
@Composable
fun AdBanner(
    screen: Screen,
    bannerStateController: BannerStateController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!bannerStateController.shouldShowBanner(screen)) return
    if (context !is Activity) return
    val activity = context

    val adView = remember {
        createAdView(activity)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                adView.adListener = object : com.google.android.gms.ads.AdListener() {}
                adView.destroy()
            } catch (_: Exception) { }
        }
    }

    AndroidView(
        factory = { _ -> adView },
        modifier = modifier.then(Modifier.fillMaxWidth().heightIn(min = 50.dp))
    )
}

private fun createAdView(activity: Activity): AdView {
    val adView = AdView(activity).apply {
        setAdSize(adaptiveBannerSize(activity))
        adUnitId = activity.getString(R.string.admob_banner_id)
        adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                if (BuildConfig.DEBUG) { try { Log.d("AdMob", "Banner Loaded") } catch (_: Exception) { } }
                visibility = View.VISIBLE
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                if (BuildConfig.DEBUG) { try { Log.d("AdMob", "Banner Failed: ${error.message}") } catch (_: Exception) { } }
                visibility = View.GONE
            }
        }
    }
    if (BuildConfig.DEBUG) { try { Log.d("AdMob", "Banner loadAd called") } catch (_: Exception) { } }
    adView.loadAd(AdRequest.Builder().build())
    return adView
}

private fun adaptiveBannerSize(activity: Activity): AdSize {
    val outMetrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    activity.windowManager.defaultDisplay.getMetrics(outMetrics)
    val density = outMetrics.density
    val widthPixels = outMetrics.widthPixels
    val adWidth = (widthPixels / density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
}
