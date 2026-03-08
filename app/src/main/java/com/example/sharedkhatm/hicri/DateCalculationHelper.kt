package com.example.sharedkhatm.hicri

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tarih farkı hesapları: bugüne göre kaç gün kaldı / geçti.
 */
object DateCalculationHelper {

    /**
     * Bugünden hedef tarihe gün farkı.
     * Negatif = geçmiş, pozitif = gelecek, 0 = bugün.
     */
    fun daysBetweenTodayAnd(target: LocalDate): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), target)

    /**
     * UI için metin: "17 gün kaldı" veya "3 gün geçti" veya "Bugün"
     */
    fun formatDayOffset(days: Long): String {
        return when {
            days > 0 -> "$days gün kaldı"
            days < 0 -> "${-days} gün geçti"
            else -> "Bugün"
        }
    }
}
