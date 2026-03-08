package com.example.sharedkhatm.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel üzerinden reklam tetikleme. Activity scope (activityViewModels).
 * Banner: Screen ile. Rewarded: RewardedAdManager (Destekçi, limit 3).
 */
class AdViewModel(application: Application) : AndroidViewModel(application) {

    private val app: com.example.sharedkhatm.MyApplication?
        get() = getApplication<Application>() as? com.example.sharedkhatm.MyApplication

    private val adManager: AdManager get() = app?.adManager ?: throw IllegalStateException("AdManager not available")
    private val rewardedAdManager: RewardedAdManager get() = app?.rewardedAdManager ?: throw IllegalStateException("RewardedAdManager not available")

    val bannerStateController: BannerStateController get() = app?.bannerStateController ?: throw IllegalStateException("BannerStateController not available")
    val supportAdTracker: SupportAdTracker get() = app?.supportAdTracker ?: throw IllegalStateException("SupportAdTracker not available")

    fun shouldShowAds(context: Context): Boolean = try { adManager.shouldShowAds(context) } catch (_: Exception) { false }

    fun loadBanner(activity: Activity, container: FrameLayout?, screen: Screen) {
        if (container == null || activity.isFinishing || activity.isDestroyed) return
        try { adManager.loadBanner(activity, container, screen) } catch (_: Exception) { container.visibility = android.view.View.GONE }
    }

    fun destroyBanner(container: FrameLayout?) {
        try { adManager.destroyBanner(container) } catch (_: Exception) { }
    }

    /** Destekçi rewarded; izlenince SupportAdTracker.increment(), reklamsızlık ödülü yok. */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit) {
        try { rewardedAdManager.showRewarded(activity, onRewarded, onDismissed) } catch (_: Exception) { onDismissed() }
    }

    fun getTodaySupportCount(): Int = try { supportAdTracker.getTodayCount() } catch (_: Exception) { 0 }
    fun canSupport(): Boolean = try { supportAdTracker.canSupport() } catch (_: Exception) { false }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AdViewModel(application) as T
    }
}
