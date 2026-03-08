package com.example.sharedkhatm

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.*
import com.example.sharedkhatm.ads.AdManager
import com.example.sharedkhatm.ads.AdManagerImpl
import com.example.sharedkhatm.ads.AdPreferences
import com.example.sharedkhatm.ads.AdRemoteConfig
import com.example.sharedkhatm.ads.AppOpenManager
import com.example.sharedkhatm.ads.BannerStateController
import com.example.sharedkhatm.ads.BannerStateControllerImpl
import com.example.sharedkhatm.ads.InterstitialManager
import com.example.sharedkhatm.ads.RewardedAdManager
import com.example.sharedkhatm.ads.RewardedAdManagerImpl
import com.example.sharedkhatm.ads.SupportAdTracker
import com.example.sharedkhatm.ads.SupportAdTrackerImpl
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO)

    private var _supportAdTracker: SupportAdTracker? = null
    private var _bannerStateController: BannerStateController? = null
    private var _rewardedAdManager: RewardedAdManager? = null
    private var _adManager: AdManager? = null
    private var _interstitialManager: InterstitialManager? = null
    private var _appOpenManager: AppOpenManager? = null

    val supportAdTracker: SupportAdTracker
        get() = _supportAdTracker ?: SupportAdTrackerImpl(this).also { _supportAdTracker = it }
    val bannerStateController: BannerStateController
        get() = _bannerStateController ?: BannerStateControllerImpl(this).also { _bannerStateController = it }
    val rewardedAdManager: RewardedAdManager
        get() = _rewardedAdManager ?: RewardedAdManagerImpl(this, supportAdTracker).also { _rewardedAdManager = it }
    val adManager: AdManager
        get() = _adManager ?: AdManagerImpl(this, bannerStateController).also { _adManager = it }
    val interstitialManager: InterstitialManager
        get() = _interstitialManager ?: InterstitialManager(this).also { _interstitialManager = it }
    val appOpenManager: AppOpenManager
        get() = _appOpenManager ?: AppOpenManager(this).also { _appOpenManager = it }

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStoredMode(this)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        if (BuildConfig.DEBUG) {
            FirebaseAuth.getInstance().addAuthStateListener { a ->
                val u = a.currentUser
                Log.d("AUTH_STATE", "currentUser=${u?.uid} anonymous=${u?.isAnonymous}")
            }
        }

        AdPreferences.init(this)

        preloadFeatureGate()
        enqueueRefreshNow()
        scheduleDailyRefresh()

        // Cold start: Application onCreate içinde ağır reklam yükleme YASAK. Init/preload Coroutine + Dispatchers.IO ile ertelenir.
        appScope.launch {
            withContext(Dispatchers.IO) {
                try { AdRemoteConfig.fetchAndActivate() } catch (_: Exception) { }
            }
            delay(800)
            withContext(Dispatchers.Main) {
                try {
                    com.google.android.gms.ads.MobileAds.initialize(this@MyApplication) {
                        val builder = com.google.android.gms.ads.RequestConfiguration.Builder()
                            .setTagForChildDirectedTreatment(com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                            .setTagForUnderAgeOfConsent(com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                            .setMaxAdContentRating(com.google.android.gms.ads.RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                        if (com.example.sharedkhatm.BuildConfig.DEBUG) {
                            builder.setTestDeviceIds(com.example.sharedkhatm.ads.AdManagerImpl.TEST_DEVICE_IDS)
                        }
                        com.google.android.gms.ads.MobileAds.setRequestConfiguration(builder.build())
                        rewardedAdManager.preloadRewarded()
                        interstitialManager.preload()
                        appOpenManager.preload()
                    }
                } catch (e: Exception) {
                    Log.e("MyApplication", "AdMob init error", e)
                }
            }
        }
    }

    /**
     * RemoteConfig ve FeatureGate’i uygulama açılırken
     * background thread'te fetch eder.
     */
    private fun preloadFeatureGate() {
        appScope.launch {
            try {
                FeatureGate.refreshRemoteConfig()
                Log.d("APP_INIT", "FeatureGate RemoteConfig fetched")
            } catch (e: Exception) {
                Log.e("APP_INIT", "RemoteConfig fetch error: ${e.message}")
            }
        }
    }

    private fun enqueueRefreshNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTime = OneTimeWorkRequestBuilder<PrayerTimesRefreshWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag("prayer_refresh_now")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "prayer_refresh_now",
            ExistingWorkPolicy.KEEP,
            oneTime
        )
    }

    private fun scheduleDailyRefresh() {
        val now = Calendar.getInstance()

        val due = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (due.timeInMillis <= now.timeInMillis) {
            due.add(Calendar.DAY_OF_MONTH, 1)
        }

        val initialDelay = due.timeInMillis - now.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<PrayerTimesRefreshWorker>(
            24,
            TimeUnit.HOURS,
            30,
            TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                5,
                TimeUnit.MINUTES
            )
            .addTag("daily_prayer_refresh")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_prayer_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
