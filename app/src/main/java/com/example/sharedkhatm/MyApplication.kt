package com.example.sharedkhatm

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

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

        // 🔥 Firebase init (main thread'de ama hafif)
        FirebaseApp.initializeApp(this)

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // 🔍 Auth state log
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { a ->
            val u = a.currentUser
            Log.d("AUTH_STATE", "currentUser=${u?.uid} anonymous=${u?.isAnonymous}")
        }

        // 🔥 RemoteConfig background fetch (ANR FIX)
        preloadFeatureGate()

        enqueueRefreshNow()
        scheduleDailyRefresh()
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
