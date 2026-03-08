package com.example.sharedkhatm.hicri

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Hicri takvim veri kaynağı. Her yıl otomatik hesaplanır, hardcoded tarih yok.
 */
class HicriTakvimRepository {

    private val helper = IslamicCalendarHelper
    private val dateHelper = DateCalculationHelper

    private fun descriptionForEvent(name: String): String = when {
        name == "Mevlid Kandili" -> "Peygamberimizin dünyaya teşrif ettiği mübarek gece"
        name == "Regaib Kandili" -> "Receb ayının ilk Cuma gecesi, rahmet ve mağfiret gecesi"
        name == "Mirac Kandili" -> "Peygamberimizin İsra ve Miraç mucizesinin yaşandığı gece"
        name == "Berat Kandili" -> "Şaban ayının 15. gecesi, mağfiret ve bereket gecesi"
        name.contains("Kadir Gecesi") -> "Bin aydan hayırlı, Kuran-ı Kerim'in indirildiği mübarek gece"
        name == "Ramazan Başlangıcı" || name == "Oruç ayının ilk günü" -> "Oruç ayının ilk günü"
        name.startsWith("Ramazan ") && name.contains(". Gün") -> "Ramazan ayı"
        name.contains("Ramazan Bayramı") && name.contains("Arefe") -> "Ramazan Bayramı öncesi arefe günü"
        name.contains("Ramazan Bayramı") -> "Şeker Bayramı - İftar bayramı"
        name.contains("Kurban Bayramı") && name.contains("Arefe") -> "Kurban Bayramı öncesi arefe günü"
        name.contains("Kurban Bayramı") -> "Kurban Bayramı"
        name == "Hicri Yılbaşı" -> "Hicri takvime göre yeni yılın ilk günü"
        name == "Üç Ayların Başlangıcı" -> "Recep, Şaban ve Ramazan aylarının başlangıcı"
        name.contains("Aşure") -> "Aşure günü"
        else -> ""
    }

    /**
     * Bugünün Hicri ve miladi metinleri.
     */
    fun getTodayHijriString(): String = helper.toHijriString(LocalDate.now())
    fun getTodayGregorianString(): String = helper.toGregorianStringTr(LocalDate.now())

    /**
     * Bugün bir özel gün aralığı içindeyse (örn. Ramazan) başlık ve aralık metni.
     * Örn: "Ramazan" ve "1 - 29 Ramazan 1447"
     */
    fun getCurrentPeriodIfAny(): Pair<String, String>? {
        val today = LocalDate.now()
        val year = today.year
        val events = helper.getSpecialDaysForGregorianYear(year)
        for (event in events) {
            if (!event.isRange) continue
            val start = event.gregorianDate
            val end = event.rangeEndDate ?: continue
            if (!today.isBefore(start) && !today.isAfter(end)) {
                val (title, range) = when (event.name) {
                    "Ramazan Başlangıcı" -> "Ramazan" to event.nameSub
                    "Ramazan Bayramı" -> "Ramazan Bayramı" to event.nameSub
                    "Kurban Bayramı" -> "Kurban Bayramı" to event.nameSub
                    else -> event.name to event.nameSub
                }
                return title to range
            }
        }
        return null
    }

    /**
     * Yaklaşan tek özel gün (başlangıç tarihi bugünden sonra olan ilk). Geçen veya bugün olanlar gösterilmez (Diyanet: 26 Recep = Mirac 15 Ocak 2026).
     */
    fun getNextSpecialDay(): HicriSpecialDayItem? {
        val today = LocalDate.now()
        val year = today.year
        val events = helper.getSpecialDaysForGregorianYear(year)
        val stillRelevant = events.filter { e ->
            e.gregorianDate.isAfter(today) && (e.rangeEndDate == null || !today.isAfter(e.rangeEndDate))
        }
        val future = stillRelevant.minByOrNull { it.gregorianDate } ?: return null
        val days = dateHelper.daysBetweenTodayAnd(future.gregorianDate)
        val displayName = if (future.name == "Ramazan Başlangıcı") "Oruç ayının ilk günü" else future.name
        return HicriSpecialDayItem(
            name = displayName,
            nameSub = future.nameSub,
            hijriDisplay = future.hijriString(),
            gregorianDate = future.gregorianDate,
            gregorianDisplay = helper.toGregorianStringTr(future.gregorianDate),
            daysOffset = days,
            dayOffsetText = dateHelper.formatDayOffset(days),
            description = descriptionForEvent(future.name),
            hijriMonthNumber = future.hijriMonth,
            rangeEndDate = future.rangeEndDate
        )
    }

    /**
     * Verilen miladi yılın tüm özel günleri. Kronolojik sıra: yıl içinde ilk gerçekleşme gününe göre.
     * Çok günlü etkinlikler (Ramazan Başlangıcı, Ramazan Bayramı, Kurban Bayramı) gün gün listelenir:
     * örn. "Kurban Bayramı (1. Gün)", "Kurban Bayramı (2. Gün)" ...
     */
    fun getAllSpecialDaysForYear(year: Int): List<HicriSpecialDayItem> {
        val events = helper.getSpecialDaysForGregorianYear(year)
        val list = mutableListOf<HicriSpecialDayItem>()
        for (event in events) {
            val end = event.rangeEndDate
            if (event.isRange && end != null) {
                var dayIndex = 1
                var d = event.gregorianDate
                // Range günleri her zaman event'in Hicri ayında listelenir (30 Ramazan Şevval'de değil Ramazan'da)
                val rangeHijriMonth = event.hijriMonth
                while (!d.isAfter(end)) {
                    val days = dateHelper.daysBetweenTodayAnd(d)
                    val (displayName, desc) = when {
                        event.name == "Ramazan Başlangıcı" && dayIndex == 1 -> "Oruç ayının ilk günü" to descriptionForEvent("Oruç ayının ilk günü")
                        event.name == "Ramazan Başlangıcı" && dayIndex > 1 -> "Ramazan $dayIndex. Gün" to descriptionForEvent("Ramazan $dayIndex. Gün")
                        else -> "${event.name} ($dayIndex. Gün)" to descriptionForEvent(event.name)
                    }
                    list.add(
                        HicriSpecialDayItem(
                            name = displayName,
                            nameSub = event.nameSub,
                            hijriDisplay = helper.toHijriString(d),
                            gregorianDate = d,
                            gregorianDisplay = helper.toGregorianStringTr(d),
                            daysOffset = days,
                            dayOffsetText = dateHelper.formatDayOffset(days),
                            description = desc,
                            hijriMonthNumber = rangeHijriMonth,
                            rangeEndDate = end
                        )
                    )
                    d = d.plusDays(1)
                    dayIndex++
                }
            } else {
                val days = dateHelper.daysBetweenTodayAnd(event.gregorianDate)
                list.add(
                    HicriSpecialDayItem(
                        name = event.name,
                        nameSub = event.nameSub,
                        hijriDisplay = event.hijriString(),
                        gregorianDate = event.gregorianDate,
                        gregorianDisplay = helper.toGregorianStringTr(event.gregorianDate),
                        daysOffset = days,
                        dayOffsetText = dateHelper.formatDayOffset(days),
                        description = descriptionForEvent(event.name),
                        hijriMonthNumber = event.hijriMonth,
                        rangeEndDate = event.rangeEndDate
                    )
                )
            }
        }
        return list.sortedBy { it.gregorianDate }
    }
}
