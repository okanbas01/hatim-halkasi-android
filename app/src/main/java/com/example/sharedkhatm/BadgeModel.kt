package com.example.sharedkhatm

/**
 * Manevi rozet modeli - performans odaklı, minimal
 */
data class BadgeModel(
    val id: String,
    val name: String,
    val description: String, // Manevi açıklama
    val icon: String, // Emoji veya drawable name
    val category: BadgeCategory,
    val isPremium: Boolean = false,
    val unlockCondition: String // Kısa açıklama: "İlk cüzü tamamla"
) {
    enum class BadgeCategory {
        PROGRESS, // İlerleme rozetleri
        STREAK,   // Seri rozetleri
        PREMIUM   // Premium rozetler
    }
}
