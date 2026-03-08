package com.example.sharedkhatm.ads

import android.content.Context
import com.example.sharedkhatm.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reklam limitleri ve politikaları Remote Config üzerinden.
 * Bot algısına takılmamak ve frekansı yönetmek için kullanılır.
 */
object AdRemoteConfig {

    private const val KEY_REWARDED_MAX_PER_DAY = "ad_rewarded_max_per_day"
    private const val KEY_REWARDED_MAX_PER_HOUR = "ad_rewarded_max_per_hour"
    private const val KEY_REWARDED_MIN_INTERVAL_MINUTES = "ad_rewarded_min_interval_minutes"
    private const val KEY_INTERSTITIAL_FREQUENCY = "ad_interstitial_frequency"       // 4 etkileşimde 1
    private const val KEY_INTERSTITIAL_MAX_PER_DAY = "ad_interstitial_max_per_day"
    private const val KEY_APP_OPEN_MAX_PER_DAY = "ad_app_open_max_per_day"
    private const val KEY_APP_OPEN_MIN_ACTIVE_DAYS = "ad_app_open_min_active_days"
    private const val KEY_BANNER_ENABLED_LOW_END = "ad_banner_enabled_low_end"      // false = düşük cihazda kapalı

    private val rc: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val minInterval = if (BuildConfig.DEBUG) 0L else 3600L
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(minInterval)
                    .build()
            )
            val defaults = hashMapOf<String, Any>(
                KEY_REWARDED_MAX_PER_DAY to 5L,
                KEY_REWARDED_MAX_PER_HOUR to 2L,
                KEY_REWARDED_MIN_INTERVAL_MINUTES to 5L,
                KEY_INTERSTITIAL_FREQUENCY to 4L,
                KEY_INTERSTITIAL_MAX_PER_DAY to 3L,
                KEY_APP_OPEN_MAX_PER_DAY to 1L,
                KEY_APP_OPEN_MIN_ACTIVE_DAYS to 3L,
                KEY_BANNER_ENABLED_LOW_END to false
            )
            setDefaultsAsync(defaults)
        }
    }

    suspend fun fetchAndActivate() {
        withContext(Dispatchers.IO) {
            try {
                rc.fetchAndActivate().await()
            } catch (_: Throwable) { }
        }
    }

    fun rewardedMaxPerDay(): Int = rc.getLong(KEY_REWARDED_MAX_PER_DAY).toInt().coerceIn(1, 10)
    fun rewardedMaxPerHour(): Int = rc.getLong(KEY_REWARDED_MAX_PER_HOUR).toInt().coerceIn(1, 5)
    fun rewardedMinIntervalMinutes(): Int = rc.getLong(KEY_REWARDED_MIN_INTERVAL_MINUTES).toInt().coerceIn(1, 60)
    fun interstitialFrequency(): Int = rc.getLong(KEY_INTERSTITIAL_FREQUENCY).toInt().coerceIn(2, 10)
    fun interstitialMaxPerDay(): Int = rc.getLong(KEY_INTERSTITIAL_MAX_PER_DAY).toInt().coerceIn(1, 5)
    fun appOpenMaxPerDay(): Int = rc.getLong(KEY_APP_OPEN_MAX_PER_DAY).toInt().coerceIn(1, 3)
    fun appOpenMinActiveDays(): Int = rc.getLong(KEY_APP_OPEN_MIN_ACTIVE_DAYS).toInt().coerceIn(1, 7)
    fun bannerEnabledOnLowEnd(): Boolean = rc.getBoolean(KEY_BANNER_ENABLED_LOW_END)
}
