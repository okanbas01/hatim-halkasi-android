package com.example.sharedkhatm.ads

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout

/**
 * Reklam soyutlaması. Banner ekran politikası BannerStateController, rewarded RewardedAdManager.
 * DI uyumlu; singleton değil.
 */
interface AdManager {

    fun shouldShowAds(context: Context): Boolean

    /** Sadece shouldShowBanner(screen) ve shouldShowAds true ise yükler. */
    fun loadBanner(activity: Activity, container: FrameLayout, screen: Screen)

    fun destroyBanner(container: FrameLayout?)
}
