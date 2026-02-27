package com.example.sharedkhatm

/**
 * İslami bilgi yarışması sorusu.
 * Kur'an, siyer, 5 şart, 6 iman esası, tarihi net bilgiler – tartışmasız konular.
 */
data class QuizQuestion(
    val id: Int,
    val soru: String,
    val siklar: List<String>,
    val dogruIndex: Int,
    val kategori: String
) {
    init {
        require(siklar.size == 4) { "4 şık olmalı" }
        require(dogruIndex in 0..3) { "dogruIndex 0-3 arası olmalı" }
    }
}
