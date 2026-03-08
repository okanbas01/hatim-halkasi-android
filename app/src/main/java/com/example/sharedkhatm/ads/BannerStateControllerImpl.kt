package com.example.sharedkhatm.ads

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.sharedkhatm.BuildConfig

/**
 * Banner politikası: Sadece ana sayfa (HOME) en alt bölümde, scroll ile görünür.
 * Sabit sticky banner yok. Düşük performanslı cihazlarda (Android 8 altı veya <3GB RAM) kapalı.
 */
class BannerStateControllerImpl(private val context: Context) : BannerStateController {

    override fun shouldShowBanner(screen: Screen): Boolean {
        AdPreferences.init(context)
        if (AdPreferences.isPremium()) {
            if (BuildConfig.DEBUG) { try { Log.d("AdMob", "shouldShowBanner: false (premium)") } catch (_: Exception) { } }
            return false
        }
        if (screen != Screen.HOME && screen != Screen.QUIZ) {
            if (BuildConfig.DEBUG) { try { Log.d("AdMob", "shouldShowBanner: false (only HOME/QUIZ)") } catch (_: Exception) { } }
            return false
        }
        if (isLowEndDevice()) {
            if (!AdRemoteConfig.bannerEnabledOnLowEnd()) {
                if (BuildConfig.DEBUG) { try { Log.d("AdMob", "shouldShowBanner: false (low-end)") } catch (_: Exception) { } }
                return false
            }
        }
        if (BuildConfig.DEBUG) { try { Log.d("AdMob", "shouldShowBanner: true ($screen)") } catch (_: Exception) { } }
        return true
    }

    private fun isLowEndDevice(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        return totalMb < 3072
    }
}
