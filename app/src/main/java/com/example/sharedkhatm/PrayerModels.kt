package com.example.sharedkhatm

import androidx.annotation.Keep

@Keep
data class PrayerResponse(
    val code: Int,
    val status: String,
    val data: PrayerData
)
@Keep
data class PrayerData(
    val timings: Timings
)
@Keep
data class Timings(
    val Fajr: String,    // İmsak
    val Sunrise: String, // Güneş
    val Dhuhr: String,   // Öğle
    val Asr: String,     // İkindi
    val Sunset: String,  // Akşam (Güneş batışı)
    val Maghrib: String, // Akşam
    val Isha: String     // Yatsı
)