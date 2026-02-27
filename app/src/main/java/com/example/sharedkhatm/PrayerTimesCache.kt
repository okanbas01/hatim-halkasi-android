package com.example.sharedkhatm

import android.content.Context

data class PrayerTimesLite(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

object PrayerTimesCache {
    private const val PREF = "PrayerTimesCache"

    fun write(context: Context, t: PrayerTimesLite) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putString("Fajr", t.fajr)
            .putString("Sunrise", t.sunrise)
            .putString("Dhuhr", t.dhuhr)
            .putString("Asr", t.asr)
            .putString("Maghrib", t.maghrib)
            .putString("Isha", t.isha)
            .apply()
    }

    fun read(context: Context): PrayerTimesLite? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val fajr = p.getString("Fajr", null) ?: return null
        val sunrise = p.getString("Sunrise", null) ?: return null
        val dhuhr = p.getString("Dhuhr", null) ?: return null
        val asr = p.getString("Asr", null) ?: return null
        val maghrib = p.getString("Maghrib", null) ?: return null
        val isha = p.getString("Isha", null) ?: return null

        return PrayerTimesLite(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
