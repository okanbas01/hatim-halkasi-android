package com.example.sharedkhatm
import androidx.annotation.Keep

@Keep
data class EsmaName(
    val arabic: String,   // Arapça Yazılışı
    val name: String,     // Türkçe Okunuşu (Allah, Er-Rahman)
    val meaning: String,  // Anlamı
    val count: Int        // Zikir Sayısı (Ebced değeri)
)