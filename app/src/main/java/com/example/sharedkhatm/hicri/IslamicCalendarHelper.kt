package com.example.sharedkhatm.hicri

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Hicri (İslami) takvim hesaplaması.
 * Türkiye Diyanet takvimine uyum için, Diyanet'in yayınladığı ay başları kullanılır (mevcut yıllar).
 * Diyanet verisi yoksa Java Umm al-Qura (HijrahDate) ile hesaplanır.
 *
 * Sayı anlamı: "10 Ramazan" = Ramazan ayının 10. günü (Hicri gün numarası).
 * Fark nedeni: Umm al-Qura (Suudi) ile Diyanet (Türkiye) ay başlangıçları bazen 1 gün farklıdır.
 */
object IslamicCalendarHelper {

    private val TURKISH = Locale("tr")

    /** Diyanet 2026: (1. günün miladi tarihi, Hicri ay no, Hicri yıl). Kaynak: Diyanet dini günler listesi. */
    private val DIYANET_2026_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2026, 1, 20), 8, 1447),   // 1 Şaban 1447
        Triple(LocalDate.of(2026, 2, 19), 9, 1447),   // 1 Ramazan 1447
        Triple(LocalDate.of(2026, 3, 20), 10, 1447),  // 1 Şevval 1447
        Triple(LocalDate.of(2026, 4, 18), 11, 1447),  // 1 Zilkade 1447
        Triple(LocalDate.of(2026, 5, 18), 12, 1447),  // 1 Zilhicce 1447
        Triple(LocalDate.of(2026, 6, 16), 1, 1448),   // 1 Muharrem 1448
        Triple(LocalDate.of(2026, 7, 15), 2, 1448),   // 1 Safer 1448
        Triple(LocalDate.of(2026, 8, 14), 3, 1448),   // 1 Rebiülevvel 1448
        Triple(LocalDate.of(2026, 9, 12), 4, 1448),   // 1 Rebiülahir 1448
        Triple(LocalDate.of(2026, 10, 12), 5, 1448),  // 1 Cemaziyelevvel 1448
        Triple(LocalDate.of(2026, 11, 10), 6, 1448),  // 1 Cemaziyelahir 1448
        Triple(LocalDate.of(2026, 12, 10), 7, 1448),  // 1 Recep 1448
    )

    /** Diyanet 2027: 1448/1449 (Diyanet 2027 dini günler listesi). */
    private val DIYANET_2027_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2026, 12, 10), 7, 1448),  // 1 Recep 1448
        Triple(LocalDate.of(2027, 1, 9), 8, 1448),    // 1 Şaban 1448
        Triple(LocalDate.of(2027, 2, 8), 9, 1448),    // 1 Ramazan 1448
        Triple(LocalDate.of(2027, 3, 9), 10, 1448),   // 1 Şevval 1448
        Triple(LocalDate.of(2027, 4, 7), 11, 1448),   // 1 Zilkade 1448
        Triple(LocalDate.of(2027, 5, 7), 12, 1448),  // 1 Zilhicce 1448
        Triple(LocalDate.of(2027, 6, 6), 1, 1449),   // 1 Muharrem 1449
        Triple(LocalDate.of(2027, 7, 5), 2, 1449),    // 1 Safer 1449
        Triple(LocalDate.of(2027, 8, 3), 3, 1449),    // 1 Rebiülevvel 1449
        Triple(LocalDate.of(2027, 9, 1), 4, 1449),    // 1 Rebiülahir 1449
        Triple(LocalDate.of(2027, 9, 30), 5, 1449),   // 1 Cemaziyelevvel 1449
        Triple(LocalDate.of(2027, 10, 29), 6, 1449),  // 1 Cemaziyelahir 1449
        Triple(LocalDate.of(2027, 11, 29), 7, 1449),  // 1 Recep 1449
        Triple(LocalDate.of(2027, 12, 29), 8, 1449),  // 1 Şaban 1449
    )

    /** Diyanet 2028: 1449/1450. */
    private val DIYANET_2028_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2027, 12, 29), 8, 1449),  // 1 Şaban 1449
        Triple(LocalDate.of(2028, 1, 28), 9, 1449),   // 1 Ramazan 1449
        Triple(LocalDate.of(2028, 2, 26), 10, 1449),  // 1 Şevval 1449
        Triple(LocalDate.of(2028, 3, 27), 11, 1449),  // 1 Zilkade 1449
        Triple(LocalDate.of(2028, 4, 26), 12, 1449),  // 1 Zilhicce 1449 (13 Zilhicce=8 May)
        Triple(LocalDate.of(2028, 5, 25), 1, 1450),   // 1 Muharrem 1450
        Triple(LocalDate.of(2028, 6, 24), 2, 1450),   // 1 Safer 1450
        Triple(LocalDate.of(2028, 7, 23), 3, 1450),   // 1 Rebiülevvel 1450
        Triple(LocalDate.of(2028, 8, 21), 4, 1450),   // 1 Rebiülahir 1450
        Triple(LocalDate.of(2028, 9, 20), 5, 1450),   // 1 Cemaziyelevvel 1450
        Triple(LocalDate.of(2028, 10, 19), 6, 1450),  // 1 Cemaziyelahir 1450
        Triple(LocalDate.of(2028, 11, 18), 7, 1450),  // 1 Recep 1450
        Triple(LocalDate.of(2028, 12, 18), 8, 1450),  // 1 Şaban 1450
    )

    /** Diyanet 2029: 1450/1451. */
    private val DIYANET_2029_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2028, 12, 18), 8, 1450),  // 1 Şaban 1450
        Triple(LocalDate.of(2029, 1, 16), 9, 1450),   // 1 Ramazan 1450
        Triple(LocalDate.of(2029, 2, 14), 10, 1450),  // 1 Şevval 1450
        Triple(LocalDate.of(2029, 3, 15), 11, 1450),  // 1 Zilkade 1450
        Triple(LocalDate.of(2029, 4, 15), 12, 1450),  // 1 Zilhicce 1450 (10 Zilhicce=24 Nisan)
        Triple(LocalDate.of(2029, 5, 14), 1, 1451),   // 1 Muharrem 1451
        Triple(LocalDate.of(2029, 6, 12), 2, 1451),   // 1 Safer 1451
        Triple(LocalDate.of(2029, 7, 12), 3, 1451),   // 1 Rebiülevvel 1451
        Triple(LocalDate.of(2029, 8, 10), 4, 1451),   // 1 Rebiülahir 1451
        Triple(LocalDate.of(2029, 9, 9), 5, 1451),    // 1 Cemaziyelevvel 1451
        Triple(LocalDate.of(2029, 10, 8), 6, 1451),   // 1 Cemaziyelahir 1451
        Triple(LocalDate.of(2029, 11, 7), 7, 1451),   // 1 Recep 1451
        Triple(LocalDate.of(2029, 12, 7), 8, 1451),   // 1 Şaban 1451
    )

    /** Diyanet 2030: 1451/1452. */
    private val DIYANET_2030_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2029, 12, 7), 8, 1451),   // 1 Şaban 1451
        Triple(LocalDate.of(2030, 1, 5), 9, 1451),    // 1 Ramazan 1451
        Triple(LocalDate.of(2030, 2, 4), 10, 1451),    // 1 Şevval 1451
        Triple(LocalDate.of(2030, 3, 5), 11, 1451),   // 1 Zilkade 1451
        Triple(LocalDate.of(2030, 4, 4), 12, 1451),   // 1 Zilhicce 1451
        Triple(LocalDate.of(2030, 5, 3), 1, 1452),    // 1 Muharrem 1452
        Triple(LocalDate.of(2030, 6, 2), 2, 1452),    // 1 Safer 1452
        Triple(LocalDate.of(2030, 7, 1), 3, 1452),    // 1 Rebiülevvel 1452
        Triple(LocalDate.of(2030, 7, 31), 4, 1452),   // 1 Rebiülahir 1452
        Triple(LocalDate.of(2030, 8, 29), 5, 1452),   // 1 Cemaziyelevvel 1452
        Triple(LocalDate.of(2030, 9, 28), 6, 1452),   // 1 Cemaziyelahir 1452
        Triple(LocalDate.of(2030, 10, 27), 7, 1452),  // 1 Recep 1452
        Triple(LocalDate.of(2030, 11, 26), 8, 1452),  // 1 Şaban 1452
    )

    /** Diyanet 2025: ay başları (vakithesaplama.diyanet.gov.tr/dinigunler.php?yil=2025) */
    private val DIYANET_2025_MONTH_STARTS = listOf(
        Triple(LocalDate.of(2025, 1, 1), 7, 1446),    // 1 Recep 1446
        Triple(LocalDate.of(2025, 1, 31), 8, 1446),   // 1 Şaban 1446
        Triple(LocalDate.of(2025, 3, 1), 9, 1446),    // 1 Ramazan 1446
        Triple(LocalDate.of(2025, 3, 30), 10, 1446),  // 1 Şevval 1446
        Triple(LocalDate.of(2025, 4, 28), 11, 1446),  // 1 Zilkade 1446
        Triple(LocalDate.of(2025, 5, 28), 12, 1446), // 1 Zilhicce 1446
        Triple(LocalDate.of(2025, 6, 26), 1, 1447),   // 1 Muharrem 1447
        Triple(LocalDate.of(2025, 7, 26), 2, 1447),   // 1 Safer 1447
        Triple(LocalDate.of(2025, 8, 24), 3, 1447),  // 1 Rebiülevvel 1447
        Triple(LocalDate.of(2025, 9, 23), 4, 1447),   // 1 Rebiülahir 1447
        Triple(LocalDate.of(2025, 10, 22), 5, 1447), // 1 Cemaziyelevvel 1447
        Triple(LocalDate.of(2025, 11, 21), 6, 1447),  // 1 Cemaziyelahir 1447
        Triple(LocalDate.of(2025, 12, 20), 7, 1447),  // 1 Recep 1447
    )

    private val HIJRI_MONTH_NAMES_TR = arrayOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
        "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"
    )

    private val GREGORIAN_DAY_NAMES_TR = arrayOf(
        "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
    )

    private val GREGORIAN_MONTH_NAMES_TR = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    /** Diyanet tablosundan (gün, ay, yıl) döner; yoksa null. 2025–2030 Diyanet listesi. */
    private fun diyanetHijri(gregorianDate: LocalDate): Triple<Int, Int, Int>? {
        val year = gregorianDate.get(ChronoField.YEAR)
        val table = when (year) {
            2025 -> DIYANET_2025_MONTH_STARTS
            2026 -> DIYANET_2026_MONTH_STARTS
            2027 -> DIYANET_2027_MONTH_STARTS
            2028 -> DIYANET_2028_MONTH_STARTS
            2029 -> DIYANET_2029_MONTH_STARTS
            2030 -> DIYANET_2030_MONTH_STARTS
            else -> return null
        }
        // Tarihten önce veya eşit en son ay başını bul
        val valid = table.filter { it.first <= gregorianDate }
        if (valid.isEmpty()) return null
        val (firstDay, hijriMonth, hijriYear) = valid.maxByOrNull { it.first }!!
        val dayOfMonth = 1 + firstDay.until(gregorianDate, ChronoUnit.DAYS).toInt()
        if (dayOfMonth < 1 || dayOfMonth > 30) return null
        return Triple(dayOfMonth, hijriMonth, hijriYear)
    }

    /**
     * Verilen miladi tarihin Hicri karşılığını metin olarak döner.
     * Diyanet tablosu varsa onu kullanır (Türkiye ile uyumlu), yoksa Umm al-Qura.
     * Örnek: "9 Ramazan 1447 H"
     */
    fun toHijriString(gregorianDate: LocalDate): String {
        val (day, month, year) = diyanetHijri(gregorianDate)
            ?: run {
                val hijri = HijrahDate.from(gregorianDate)
                Triple(
                    hijri.get(ChronoField.DAY_OF_MONTH),
                    hijri.get(ChronoField.MONTH_OF_YEAR),
                    hijri.get(ChronoField.YEAR)
                )
            }
        val monthName = if (month in 1..12) HIJRI_MONTH_NAMES_TR[month - 1] else ""
        return "$day $monthName $year H"
    }

    /**
     * Hicri ay numarası (1-12) ve yılı döner. Diyanet tablosu varsa ona göre.
     */
    fun getHijriMonthYear(gregorianDate: LocalDate): Pair<Int, Int> {
        val t = diyanetHijri(gregorianDate)
        if (t != null) return Pair(t.second, t.third)
        val hijri = HijrahDate.from(gregorianDate)
        return hijri.get(ChronoField.MONTH_OF_YEAR) to hijri.get(ChronoField.YEAR)
    }

    /**
     * Verilen miladi tarihin Hicri gün numarasını döner (1-30). Diyanet tablosu varsa ona göre.
     */
    fun getHijriDayOfMonth(gregorianDate: LocalDate): Int =
        diyanetHijri(gregorianDate)?.first ?: HijrahDate.from(gregorianDate).get(ChronoField.DAY_OF_MONTH)

    /** Hicri ay adı (1-12). */
    fun getHijriMonthName(monthNumber: Int): String {
        val i = (monthNumber - 1).coerceIn(0, HIJRI_MONTH_NAMES_TR.size - 1)
        return HIJRI_MONTH_NAMES_TR[i]
    }

    /** "Şevval 10. Ay" formatında ay gösterimi. */
    fun getMonthNameWithNumber(monthNumber: Int): String {
        return "${getHijriMonthName(monthNumber)} $monthNumber. Ay"
    }

    /** Miladi tarih kısa: "27 Şubat 2026" (gün adı yok). */
    fun toGregorianShort(gregorianDate: LocalDate): String {
        val monthIndex = gregorianDate.get(ChronoField.MONTH_OF_YEAR) - 1
        val monthName = if (monthIndex in GREGORIAN_MONTH_NAMES_TR.indices) GREGORIAN_MONTH_NAMES_TR[monthIndex] else ""
        return "${gregorianDate.get(ChronoField.DAY_OF_MONTH)} $monthName ${gregorianDate.get(ChronoField.YEAR)}"
    }

    /**
     * Miladi tarihi Türkçe metin: "27 Şubat 2026 Cuma"
     */
    fun toGregorianStringTr(gregorianDate: LocalDate): String {
        val dayIndex = gregorianDate.get(ChronoField.DAY_OF_WEEK) - 1 // ISO: Mon=1..Sun=7 -> index 0..6
        val dayName = if (dayIndex in GREGORIAN_DAY_NAMES_TR.indices) GREGORIAN_DAY_NAMES_TR[dayIndex] else ""
        val monthIndex = gregorianDate.get(ChronoField.MONTH_OF_YEAR) - 1
        val monthName = if (monthIndex in GREGORIAN_MONTH_NAMES_TR.indices) GREGORIAN_MONTH_NAMES_TR[monthIndex] else ""
        return "${gregorianDate.get(ChronoField.DAY_OF_MONTH)} $monthName ${gregorianDate.get(ChronoField.YEAR)} $dayName"
    }

    /**
     * Bir Hicri ayın o yıldaki gün sayısı (29 veya 30). Umm al-Qura'ya göre.
     */
    fun getLengthOfHijriMonth(hijriYear: Int, hijriMonth: Int): Int {
        val firstOfMonth = HijrahDate.of(hijriYear, hijriMonth, 1)
        val firstOfNext = firstOfMonth.plus(1, ChronoUnit.MONTHS)
        return firstOfMonth.until(firstOfNext, ChronoUnit.DAYS).toInt()
    }

    /**
     * Bugünden verilen tarihe kadar gün farkı. Negatif = geçmiş, pozitif = gelecek.
     */
    fun daysFromToday(target: LocalDate): Long {
        return ChronoUnit.DAYS.between(LocalDate.now(), target)
    }

    /**
     * Özel gün adı ve o güne ait tarih bilgisi. Aralık günler (Ramazan, bayram) için start/end kullanılır.
     */
    data class SpecialDayEvent(
        val name: String,
        val nameSub: String = "",
        val gregorianDate: LocalDate,
        val hijriDay: Int,
        val hijriMonth: Int,
        val hijriYear: Int,
        val isRange: Boolean = false,
        val rangeEndDate: LocalDate? = null
    ) {
        fun hijriString(): String = formatHijri(this)
    }

    private fun formatHijri(e: SpecialDayEvent): String {
        val monthName = if (e.hijriMonth in 1..12) HIJRI_MONTH_NAMES_TR[e.hijriMonth - 1] else ""
        return if (e.isRange && e.rangeEndDate != null) {
            val endHijri = HijrahDate.from(e.rangeEndDate)
            "${e.hijriDay} - ${endHijri.get(ChronoField.DAY_OF_MONTH)} $monthName ${e.hijriYear}"
        } else {
            "${e.hijriDay} $monthName ${e.hijriYear} H"
        }
    }

    /**
     * Verilen miladi yıl için İslami özel günleri hesaplar (Diyanet takvimine uyumlu isimler).
     * Ramazan başlangıç, bayramlar, kandiller, arefe.
     */
    fun getSpecialDaysForGregorianYear(year: Int): List<SpecialDayEvent> {
        val list = mutableListOf<SpecialDayEvent>()
        val jan1 = LocalDate.of(year, 1, 1)
        val dec31 = LocalDate.of(year, 12, 31)

        val startHijri = HijrahDate.from(jan1)
        val endHijri = HijrahDate.from(dec31)
        val startHy = startHijri.get(ChronoField.YEAR)
        val endHy = endHijri.get(ChronoField.YEAR)

        for (hy in startHy..endHy) {
            // Hicri Yılbaşı: 1 Muharrem (Diyanet dini günler listesi)
            addIfInRange(list, hy, 1, 1, jan1, dec31, "Hicri Yılbaşı")

            // Aşure Günü: 10 Muharrem (Diyanet dini günler listesi)
            addIfInRange(list, hy, 1, 10, jan1, dec31, "Aşure Günü")

            // Üç Ayların Başlangıcı: 1 Recep (Diyanet dini günler listesi)
            addIfInRange(list, hy, 7, 1, jan1, dec31, "Üç Ayların Başlangıcı")

            // Mevlid Kandili: 11 Rebiülevvel (Diyanet vakithesaplama.diyanet.gov.tr/dinigunler.php)
            addIfInRange(list, hy, 3, 11, jan1, dec31, "Mevlid Kandili")

            // Regaib Kandili: Recep ayının ilk Cuma gecesi = ilk Perşembe günü (Diyanet)
            val regaibDate = firstThursdayOfHijriMonth(hy, 7)
            if (regaibDate != null && !regaibDate.isBefore(jan1) && !regaibDate.isAfter(dec31))
                list.add(SpecialDayEvent("Regaib Kandili", "", regaibDate, 1, 7, hy))

            // Mirac Kandili: 26 Recep (Diyanet; örn. 2026: 15 Ocak)
            addIfInRange(list, hy, 7, 26, jan1, dec31, "Mirac Kandili")

            // Berat Kandili: 14 Şaban (Diyanet resmi takvim)
            addIfInRange(list, hy, 8, 14, jan1, dec31, "Berat Kandili")

            // Ramazan başlangıcı: 1 Ramazan
            val ramazan1 = gregorianFromHijri(hy, 9, 1) ?: continue
            if (!ramazan1.isBefore(jan1) && !ramazan1.isAfter(dec31)) {
                val len = getLengthOfHijriMonth(hy, 9)
                val ramazanEnd = gregorianFromHijri(hy, 9, len)
                list.add(SpecialDayEvent("Ramazan Başlangıcı", "1 - $len Ramazan $hy", ramazan1, 1, 9, hy, isRange = true, rangeEndDate = ramazanEnd))
            }

            // Kadir Gecesi: 26 Ramazan (Diyanet resmi takvim)
            addIfInRange(list, hy, 9, 26, jan1, dec31, "Kadir Gecesi")

            // Ramazan Bayramı Arefesi: Ramazan'ın son günü
            val ramazanSonGun = getLengthOfHijriMonth(hy, 9)
            addIfInRange(list, hy, 9, ramazanSonGun, jan1, dec31, "Ramazan Bayramı Arefesi")

            // Ramazan Bayramı: 1-3 Şevval
            val sevval1 = gregorianFromHijri(hy, 10, 1)
            if (sevval1 != null && !sevval1.isBefore(jan1) && !sevval1.isAfter(dec31)) {
                val sevval3 = gregorianFromHijri(hy, 10, 3)
                list.add(SpecialDayEvent("Ramazan Bayramı", "1 - 3 Şevval $hy", sevval1, 1, 10, hy, isRange = true, rangeEndDate = sevval3))
            }

            // Kurban Bayramı Arefesi: 9 Zilhicce
            addIfInRange(list, hy, 12, 9, jan1, dec31, "Kurban Bayramı Arefesi")

            // Kurban Bayramı: 10 Zilhicce (1. gün)
            val zilhicce10 = gregorianFromHijri(hy, 12, 10)
            if (zilhicce10 != null && !zilhicce10.isBefore(jan1) && !zilhicce10.isAfter(dec31)) {
                val zilhicce13 = gregorianFromHijri(hy, 12, 13)
                list.add(SpecialDayEvent("Kurban Bayramı", "10 - 13 Zilhicce $hy", zilhicce10, 10, 12, hy, isRange = true, rangeEndDate = zilhicce13))
            }
        }

        return list.distinctBy { "${it.name}-${it.gregorianDate}-${it.hijriYear}-${it.hijriMonth}-${it.hijriDay}" }.sortedBy { it.gregorianDate }
    }

    private fun addIfInRange(
        list: MutableList<SpecialDayEvent>,
        hy: Int, month: Int, day: Int,
        rangeStart: LocalDate, rangeEnd: LocalDate,
        name: String
    ) {
        val g = gregorianFromHijri(hy, month, day) ?: return
        if (!g.isBefore(rangeStart) && !g.isAfter(rangeEnd))
            list.add(SpecialDayEvent(name, "", g, day, month, hy))
    }

    /** Recep (7) ayının ilk Perşembe gününü bul (Regaib gecesi = perşembe akşamı). */
    private fun firstThursdayOfHijriMonth(hy: Int, month: Int): LocalDate? {
        val first = gregorianFromHijri(hy, month, 1) ?: return null
        var d = first
        for (i in 0..6) {
            if (d.get(ChronoField.DAY_OF_WEEK) == 4) return d // Thursday = 4 in ISO (Mon=1..Sun=7) - actually ISO: Mon=1, Thu=4
            d = d.plusDays(1)
        }
        return null
    }

    /** Hicri günü miladi LocalDate'e çevirir. Diyanet tablosu varsa (2025/2026) onu kullanır; yoksa Umm al-Qura. */
    private fun gregorianFromHijri(hy: Int, month: Int, day: Int): LocalDate? {
        return diyanetGregorianFromHijri(hy, month, day) ?: gregorianFromHijriUmmAlQura(hy, month, day)
    }

    /** Diyanet tablosundan Hicri -> Miladi (2025–2030). 1 Ramazan 1447 = 19 Şubat 2026. */
    private fun diyanetGregorianFromHijri(hy: Int, month: Int, day: Int): LocalDate? {
        val table = DIYANET_2025_MONTH_STARTS + DIYANET_2026_MONTH_STARTS +
                DIYANET_2027_MONTH_STARTS + DIYANET_2028_MONTH_STARTS +
                DIYANET_2029_MONTH_STARTS + DIYANET_2030_MONTH_STARTS
        val firstOfMonth = table.find { it.second == month && it.third == hy }?.first ?: return null
        return firstOfMonth.plusDays((day - 1).toLong())
    }

    /** Umm al-Qura ile Hicri -> Miladi (Diyanet tablosu yoksa). */
    private fun gregorianFromHijriUmmAlQura(hy: Int, month: Int, day: Int): LocalDate? {
        return try {
            val hijri = HijrahDate.of(hy, month, day)
            LocalDate.from(hijri)
        } catch (_: Exception) {
            null
        }
    }
}
