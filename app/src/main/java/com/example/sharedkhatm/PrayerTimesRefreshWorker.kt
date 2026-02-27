package com.example.sharedkhatm

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PrayerTimesRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val locPrefs = applicationContext.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        val appPrefs = applicationContext.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)

        // Namaz bildirimleri tamamen kapalıysa gereksiz yenileme yapma
        val anyPrayerEnabled =
            appPrefs.getBoolean("notif_prayer", true) || appPrefs.getBoolean("notif_kerahat", false)
        if (!anyPrayerEnabled) {
            Log.d("PrayerWorker", "All prayer notifications disabled -> skip")
            return Result.success()
        }

        // lat/lon yoksa çalıştırma
        val lat = locPrefs.getFloat("lat", 0f).toDouble()
        val lon = locPrefs.getFloat("long", 0f).toDouble()
        if (lat == 0.0 || lon == 0.0) {
            Log.d("PrayerWorker", "No location (lat/lon=0) -> skip")
            return Result.success()
        }

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.aladhan.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(PrayerApiService::class.java)
            val tuneStr = "1,1,0,5,4,6,0,-1,0" // Diyanet İstanbul: Öğle +5, İkindi +4, Akşam +6

            val response = service.getTimings(lat = lat, long = lon, tune = tuneStr).execute()
            if (!response.isSuccessful || response.body() == null) {
                Log.w("PrayerWorker", "API failed: ${response.code()}")
                return Result.retry()
            }

            val t = response.body()!!.data.timings

            val lite = PrayerTimesLite(
                fajr = t.Fajr,
                sunrise = t.Sunrise,
                dhuhr = t.Dhuhr,
                asr = t.Asr,
                maghrib = t.Maghrib,
                isha = t.Isha
            )

            // 1) Cache'e yaz
            PrayerTimesCache.write(applicationContext, lite)

            // 2) UI için LocationPrefs içinde de tut
            locPrefs.edit()
                .putString("saved_fajr", lite.fajr)
                .putString("saved_sunrise", lite.sunrise)
                .putString("saved_dhuhr", lite.dhuhr)
                .putString("saved_asr", lite.asr)
                .putString("saved_maghrib", lite.maghrib)
                .putString("saved_isha", lite.isha)
                .apply()

            // 3) Alarm’ları yeniden kur
            PrayerReminderScheduler(applicationContext).rescheduleAll(lite)

            Log.d("PrayerWorker", "Timings refreshed & alarms rescheduled")
            Result.success()

        } catch (e: Exception) {
            Log.e("PrayerWorker", "Exception: ${e.message}", e)
            Result.retry()
        }
    }
}
