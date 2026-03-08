package com.example.sharedkhatm.hicri

import java.time.LocalDate

/**
 * Liste ve kartta gösterilecek özel gün modeli.
 * description: Özel günün kısa açıklaması (alt satırda gösterilir).
 * hijriMonthNumber: Hicri ay numarası (1-12), "Şevval 10. Ay" formatı için.
 * rangeEndDate: Aralık günlerinde (Ramazan, bayram vb.) bitiş tarihi; geçtiyse listede gösterilmez.
 */
data class HicriSpecialDayItem(
    val name: String,
    val nameSub: String,
    val hijriDisplay: String,
    val gregorianDate: LocalDate,
    val gregorianDisplay: String,
    val daysOffset: Long,
    val dayOffsetText: String,
    val description: String = "",
    val hijriMonthNumber: Int = 9,
    val rangeEndDate: LocalDate? = null
) {
    val isPast: Boolean get() = daysOffset < 0
    val isToday: Boolean get() = daysOffset == 0L
    /** Aralık bitmişse true (artık listede gösterilmemeli). */
    fun isRangeEnded(today: LocalDate): Boolean = rangeEndDate != null && today.isAfter(rangeEndDate)
}
