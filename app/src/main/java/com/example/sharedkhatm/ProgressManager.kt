package com.example.sharedkhatm

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Performans odaklı ilerleme takip sistemi
 * - Hafif SharedPreferences kullanımı
 * - Günlük seri takibi
 * - Mikro hedef takibi
 * - Rozet sistemi
 * - O(1) kontrol, gereksiz işlem yok
 */
object ProgressManager {
    private const val PREFS_NAME = "ProgressManager"
    private const val KEY_STREAK_DAYS = "streak_days"
    private const val KEY_LAST_READ_DATE = "last_read_date"
    private const val KEY_DAILY_PAGES = "daily_pages"
    private const val KEY_DAILY_MINUTES = "daily_minutes"
    private const val KEY_BADGES = "badges" // JSON string: ["badge1", "badge2"]
    
    // Rozet ID'leri
    const val BADGE_FIRST_JUZ = "first_juz"
    const val BADGE_7_DAY_STREAK = "7_day_streak"
    const val BADGE_FIRST_HATIM = "first_hatim"
    const val BADGE_100_PAGES = "100_pages"
    
    /**
     * Bugün okuma yapıldı mı kontrol et ve seriyi güncelle
     * Performans: O(1), sadece SharedPreferences okuma/yazma
     */
    fun recordReading(context: Context, pagesRead: Int = 1, minutesRead: Int = 0) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayString()
        val lastDate = prefs.getString(KEY_LAST_READ_DATE, "") ?: ""
        
        if (today != lastDate) {
            // Yeni gün - seriyi kontrol et
            updateStreak(context, prefs, today, lastDate)
            // Günlük sayaçları sıfırla
            prefs.edit()
                .putInt(KEY_DAILY_PAGES, pagesRead)
                .putInt(KEY_DAILY_MINUTES, minutesRead)
                .putString(KEY_LAST_READ_DATE, today)
                .apply()
        } else {
            // Aynı gün - sadece sayıları artır
            val currentPages = prefs.getInt(KEY_DAILY_PAGES, 0)
            val currentMinutes = prefs.getInt(KEY_DAILY_MINUTES, 0)
            prefs.edit()
                .putInt(KEY_DAILY_PAGES, currentPages + pagesRead)
                .putInt(KEY_DAILY_MINUTES, currentMinutes + minutesRead)
                .apply()
        }
        
        // Rozet kontrolü (hafif)
        checkBadges(context, prefs, pagesRead)
    }
    
    /**
     * Seri güncelleme - hafif kontrol
     */
    private fun updateStreak(context: Context, prefs: SharedPreferences, today: String, lastDate: String) {
        if (lastDate.isEmpty()) {
            // İlk okuma
            prefs.edit().putInt(KEY_STREAK_DAYS, 1).apply()
            return
        }
        
        val lastDateCal = parseDateString(lastDate)
        val todayCal = parseDateString(today)
        
        if (lastDateCal != null && todayCal != null) {
            val daysDiff = ((todayCal.timeInMillis - lastDateCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
            
            if (daysDiff == 1) {
                // Seri devam ediyor
                val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)
                prefs.edit().putInt(KEY_STREAK_DAYS, currentStreak + 1).apply()
                
                // 7 gün seri rozeti kontrolü
                if (currentStreak + 1 == 7) {
                    awardBadge(context, prefs, BADGE_7_DAY_STREAK)
                }
            } else if (daysDiff > 1) {
                // Seri bozuldu - nazik mesaj için kaydet
                prefs.edit()
                    .putInt(KEY_STREAK_DAYS, 1)
                    .putBoolean("streak_broken", true)
                    .apply()
            }
        }
    }
    
    /**
     * Rozet kontrolü - O(1) lookup
     * BadgeManager ile entegre edildi
     */
    private fun checkBadges(context: Context, prefs: SharedPreferences, pagesRead: Int) {
        val badgesJson = prefs.getString(KEY_BADGES, "[]") ?: "[]"
        val badges = parseBadges(badgesJson).toMutableSet()
        
        // İlk cüz rozeti (henüz yoksa)
        if (!badges.contains(BADGE_FIRST_JUZ)) {
            val totalPages = getTotalPagesRead(context)
            if (totalPages >= 20) { // Yaklaşık 1 cüz = 20 sayfa
                badges.add(BADGE_FIRST_JUZ)
                awardBadge(context, prefs, BADGE_FIRST_JUZ)
                // BadgeManager'a da bildir
                BadgeManager.unlockBadge(context, BADGE_FIRST_JUZ)
            }
        }
        
        // 100 sayfa rozeti
        if (!badges.contains(BADGE_100_PAGES)) {
            val totalPages = getTotalPagesRead(context)
            if (totalPages >= 100) {
                badges.add(BADGE_100_PAGES)
                awardBadge(context, prefs, BADGE_100_PAGES)
                // BadgeManager'a da bildir
                BadgeManager.unlockBadge(context, BADGE_100_PAGES)
            }
        }
        
        // 7 gün seri rozeti (zaten var ama BadgeManager'a bildir)
        val streakDays = getStreakDays(context)
        if (streakDays >= 7 && !badges.contains(BADGE_7_DAY_STREAK)) {
            badges.add(BADGE_7_DAY_STREAK)
            awardBadge(context, prefs, BADGE_7_DAY_STREAK)
            BadgeManager.unlockBadge(context, BADGE_7_DAY_STREAK)
        }
    }
    
    /**
     * Rozet ver
     */
    private fun awardBadge(context: Context, prefs: SharedPreferences, badgeId: String) {
        val badgesJson = prefs.getString(KEY_BADGES, "[]") ?: "[]"
        val badges = parseBadges(badgesJson).toMutableSet()
        badges.add(badgeId)
        prefs.edit().putString(KEY_BADGES, badges.joinToString(",", "[", "]")).apply()
    }
    
    /**
     * Günlük seri bilgisi
     */
    fun getStreakDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayString()
        val lastDate = prefs.getString(KEY_LAST_READ_DATE, "") ?: ""
        
        // Bugün okuma yapıldı mı?
        return if (today == lastDate) {
            prefs.getInt(KEY_STREAK_DAYS, 0)
        } else {
            // Bugün henüz okuma yok - dünkü seriyi göster
            val yesterday = getYesterdayString()
            if (lastDate == yesterday) {
                prefs.getInt(KEY_STREAK_DAYS, 0)
            } else {
                0 // Seri bozulmuş
            }
        }
    }
    
    /**
     * Günlük mikro hedef bilgisi
     */
    fun getDailyProgress(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayString()
        val lastDate = prefs.getString(KEY_LAST_READ_DATE, "") ?: ""
        
        return if (today == lastDate) {
            Pair(prefs.getInt(KEY_DAILY_PAGES, 0), prefs.getInt(KEY_DAILY_MINUTES, 0))
        } else {
            Pair(0, 0)
        }
    }
    
    /**
     * Rozet listesi
     */
    fun getBadges(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val badgesJson = prefs.getString(KEY_BADGES, "[]") ?: "[]"
        return parseBadges(badgesJson)
    }
    
    /**
     * Toplam okunan sayfa (yaklaşık) - public yapıldı (BadgeManager için)
     */
    fun getTotalPagesRead(context: Context): Int {
        // Basit hesaplama - her cüz ~20 sayfa
        val userId = getUserId(context)
        val prefs = context.getSharedPreferences("UserGoal_$userId", Context.MODE_PRIVATE)
        val totalJuz = prefs.getInt("totalJuzRead", 0)
        return totalJuz * 20
    }
    
    private fun getUserId(context: Context): String {
        // Firebase auth'dan al veya guest ID
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        } catch (e: Exception) {
            "guest"
        }
    }
    
    /**
     * Tarih string'i (yyyyMMdd)
     */
    private fun getTodayString(): String {
        val cal = Calendar.getInstance()
        return String.format("%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    
    private fun getYesterdayString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return String.format("%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    
    private fun parseDateString(dateStr: String): Calendar? {
        return try {
            val year = dateStr.substring(0, 4).toInt()
            val month = dateStr.substring(4, 6).toInt() - 1
            val day = dateStr.substring(6, 8).toInt()
            Calendar.getInstance().apply {
                set(year, month, day)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseBadges(json: String): Set<String> {
        return try {
            json.removePrefix("[").removeSuffix("]")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
