package com.example.sharedkhatm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Ezan vakitleri tek kaynak: konum/şehir değişince state güncellenir,
 * Fragment sadece observe eder — fragment yeniden oluşmasına gerek kalmaz.
 */
data class PrayerTimesUiState(
    val locationName: String,
    val isGps: Boolean,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val nextPrayerDesc: String,
    val isLoading: Boolean,
    val hasData: Boolean
) {
    companion object {
        fun empty() = PrayerTimesUiState(
            locationName = "",
            isGps = false,
            fajr = "--",
            sunrise = "--",
            dhuhr = "--",
            asr = "--",
            maghrib = "--",
            isha = "--",
            nextPrayerName = "--",
            nextPrayerTime = "--:--",
            nextPrayerDesc = "",
            isLoading = false,
            hasData = false
        )
    }
}

class PrayerTimesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_LOC, android.content.Context.MODE_PRIVATE)

    private val _prayerState = MutableLiveData<PrayerTimesUiState>(PrayerTimesUiState.empty())
    val prayerState: LiveData<PrayerTimesUiState> = _prayerState

    @Volatile
    private var lastFetchedLat: Double = 0.0
    @Volatile
    private var lastFetchedLon: Double = 0.0
    private val sameCoordTolerance = 0.0001

    /** Prefs'ten okuyup state yayınla. Fragment ilk açıldığında veya geri gelince çağrılabilir. */
    fun loadFromPrefs() {
        val locationName = prefs.getString("savedCity", null) ?: ""
        val isGps = prefs.getString("locationMode", "UNKNOWN") == "GPS"
        val fajr = prefs.getString("saved_fajr", null)?.take(5) ?: "--"
        val sunrise = prefs.getString("saved_sunrise", null)?.take(5) ?: "--"
        val dhuhr = prefs.getString("saved_dhuhr", null)?.take(5) ?: "--"
        val asr = prefs.getString("saved_asr", null)?.take(5) ?: "--"
        val maghrib = prefs.getString("saved_maghrib", null)?.take(5) ?: "--"
        val isha = prefs.getString("saved_isha", null)?.take(5) ?: "--"
        val hasData = !fajr.isNullOrBlank() && fajr != "--" && !dhuhr.isNullOrBlank() && dhuhr != "--"

        val timings = if (hasData) Timings(
            Fajr = prefs.getString("saved_fajr", "") ?: "",
            Sunrise = prefs.getString("saved_sunrise", "") ?: "",
            Dhuhr = prefs.getString("saved_dhuhr", "") ?: "",
            Asr = prefs.getString("saved_asr", "") ?: "",
            Sunset = "",
            Maghrib = prefs.getString("saved_maghrib", "") ?: "",
            Isha = prefs.getString("saved_isha", "") ?: ""
        ) else null

        val (nextName, nextTime, nextDesc) = if (timings != null) {
            nextPrayerInfo(timings)
        } else Triple("--", "--:--", "")

        _prayerState.postValue(PrayerTimesUiState(
            locationName = locationName.ifBlank { "Konum Seçiniz" },
            isGps = isGps,
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            nextPrayerName = nextName,
            nextPrayerTime = nextTime,
            nextPrayerDesc = nextDesc,
            isLoading = false,
            hasData = hasData
        ))
    }

    /**
     * Konum izni veya şehir seçimi sonrası çağrılır.
     * Aynı koordinat için tekrar istek atılmaz (gereksiz network önlenir).
     */
    fun fetchPrayerTimes(lat: Double, lon: Double) {
        if (kotlin.math.abs(lat - lastFetchedLat) < sameCoordTolerance &&
            kotlin.math.abs(lon - lastFetchedLon) < sameCoordTolerance &&
            _prayerState.value?.hasData == true
        ) {
            loadFromPrefs()
            return
        }
        _prayerState.postValue((_prayerState.value ?: PrayerTimesUiState.empty()).copy(isLoading = true))

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
        // Tune: Diyanet İstanbul (Öğle +2 daha, İkindi +2 daha, Akşam +7)
        val tuneStr = "1,1,0,5,4,6,0,-1,0"
        service.getTimings(lat = lat, long = lon, tune = tuneStr).enqueue(object : Callback<PrayerResponse> {
            override fun onResponse(call: Call<PrayerResponse>, response: Response<PrayerResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val timings = response.body()!!.data.timings
                    savePrayerTimesToPrefs(timings)
                    lastFetchedLat = lat
                    lastFetchedLon = lon
                    val lite = PrayerTimesLite(
                        fajr = timings.Fajr,
                        sunrise = timings.Sunrise,
                        dhuhr = timings.Dhuhr,
                        asr = timings.Asr,
                        maghrib = timings.Maghrib,
                        isha = timings.Isha
                    )
                    PrayerTimesCache.write(getApplication(), lite)
                    schedulePrayerAlarmsOnBackground(lite)
                }
                loadFromPrefs()
            }

            override fun onFailure(call: Call<PrayerResponse>, t: Throwable) {
                loadFromPrefs()
            }
        })
    }

    private fun savePrayerTimesToPrefs(timings: Timings) {
        prefs.edit()
            .putString("saved_fajr", timings.Fajr)
            .putString("saved_sunrise", timings.Sunrise)
            .putString("saved_dhuhr", timings.Dhuhr)
            .putString("saved_asr", timings.Asr)
            .putString("saved_maghrib", timings.Maghrib)
            .putString("saved_isha", timings.Isha)
            .apply()
    }

    private fun nextPrayerInfo(timings: Timings): Triple<String, String, String> {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val nowTime = Date()
            val nowStr = sdf.format(nowTime)
            val nowParsed = sdf.parse(nowStr) ?: return Triple("--", "--:--", "")

            val prayerList = listOf(
                Triple("İMSAK", timings.Fajr, "Sabah Namazı Vakti"),
                Triple("GÜNEŞ", timings.Sunrise, "Güneş Doğuşu"),
                Triple("ÖĞLE", timings.Dhuhr, "Öğle Namazı Vakti"),
                Triple("İKİNDİ", timings.Asr, "İkindi Namazı Vakti"),
                Triple("AKŞAM", timings.Maghrib, "Akşam Namazı Vakti"),
                Triple("YATSI", timings.Isha, "Yatsı Namazı Vakti")
            )

            for ((name, timeRaw, description) in prayerList) {
                val cleanTime = timeRaw.split(" ")[0]
                val prayerDate = sdf.parse(cleanTime)
                if (prayerDate != null && prayerDate.after(nowParsed)) {
                    return Triple(name, cleanTime, description)
                }
            }
            val fajrClean = timings.Fajr.split(" ")[0]
            Triple("İMSAK", fajrClean, "Sabah Namazı Vakti")
        } catch (_: Exception) {
            Triple("--", "--:--", "")
        }
    }

    private fun schedulePrayerAlarmsOnBackground(lite: PrayerTimesLite) {
        prayerSchedulerExecutor.execute {
            try {
                PrayerReminderScheduler(getApplication()).rescheduleAll(lite)
            } catch (e: Exception) {
                android.util.Log.e("PrayerDebug", "rescheduleAll from ViewModel failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val PREFS_LOC = "LocationPrefs"
        private val prayerSchedulerExecutor = Executors.newSingleThreadExecutor()
    }
}
