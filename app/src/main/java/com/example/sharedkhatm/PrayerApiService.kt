package com.example.sharedkhatm

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PrayerApiService {
    /**
     * Aladhan API — Method 13 = Diyanet İşleri.
     * date: DD-MM-YYYY (bugün, cihaz timezone'da) — doğru gün vakitleri için.
     * timezonestring: Europe/Istanbul gibi — API'nin doğru yerel saat döndürmesi için.
     * tune: Öğle ve İkindi'yi Diyanet/Google yayını ile uyumlu olacak şekilde +2 dk (0,0,0,2,2,0,0,0,0).
     */
    @GET("v1/timings")
    fun getTimings(
        @Query("latitude") lat: Double,
        @Query("longitude") long: Double,
        @Query("method") method: Int = 13,
        @Query("date") date: String? = null,
        @Query("timezonestring") timezonestring: String? = null,
        @Query("tune") tune: String? = null
    ): Call<PrayerResponse>
}