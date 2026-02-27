package com.example.sharedkhatm
import androidx.annotation.Keep

@Keep
data class PrayerStep(
    val title: String,
    val description: String,
    val arabic: String? = null,
    val reading: String? = null,
    val meaning: String? = null,
    var isExpanded: Boolean = false
)

/**
 * Diyanet Turkey prayer section type.
 * Order of performance is defined per prayer via [PrayerSection.orderIndex].
 */
@Keep
enum class PrayerType {
    FARZ,
    SUNNET,
    ILK_SUNNET,
    SON_SUNNET
}

/**
 * One section of a daily prayer (e.g. "2 rekat Sünnet" or "4 rekat Farz").
 * Order is manually defined according to Diyanet Turkey; sort by [orderIndex].
 */
@Keep
data class PrayerSection(
    val name: String,
    val type: PrayerType,
    val rakah: Int,
    val orderIndex: Int
)