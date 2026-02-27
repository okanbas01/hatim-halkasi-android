package com.example.sharedkhatm
import androidx.annotation.Keep
@Keep
// API'den gelen ana cevap
data class QuranApiResponse(
    val code: Int,
    val status: String,
    val data: JuzData
)
@Keep
// Veri Taşıyıcı (Hem Cüz hem Sure için ortak)
data class JuzData(
    val number: Int,
    val ayahs: List<Ayah>,
    // SURE MODU İÇİN EK ALANLAR:
    val name: String? = null,        // Sure ismi (Arapça)
    val englishName: String? = null, // Sure ismi (İngilizce)
    val englishNameTranslation: String? = null // Anlamı
)
@Keep
// Ayet verisi
data class Ayah(
    val number: Int,
    val text: String,
    var textTurkish: String? = null,
    var textTransliteration: String? = null,
    var audio: String? = null,
    val numberInSurah: Int,
    var surah: Surah? = null // DİKKAT: Bunu "var" ve "nullable (?)" yaptık
)
@Keep
// Sure verisi
data class Surah(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String
)