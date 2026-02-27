package com.example.sharedkhatm

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/**
 * Manevi rozet yönetim sistemi - performans odaklı
 * O(1) kontrol, hafif veri yapısı, minimal allocation
 */
object BadgeManager {
    private const val PREFS_NAME = "BadgeManager"
    private const val KEY_UNLOCKED_BADGES = "unlocked_badges" // JSON: ["badge1", "badge2"]
    private const val KEY_SHOWN_BADGES = "shown_badges" // Gösterilmiş rozetler (tekrar gösterme)
    private const val KEY_BADGE_MIGRATION_DONE = "badge_migration_v1_done"
    
    // Tüm rozetler - statik liste (performans için)
    val allBadges = listOf(
        // İlerleme Rozetleri
        BadgeModel(
            id = ProgressManager.BADGE_FIRST_JUZ,
            name = "İlk Adım",
            description = "Kur'an okumaya başladığın için Allah razı olsun.",
            icon = "🌙",
            category = BadgeModel.BadgeCategory.PROGRESS,
            unlockCondition = "İlk cüzü tamamla"
        ),
        BadgeModel(
            id = ProgressManager.BADGE_100_PAGES,
            name = "Sabırlı Yolcu",
            description = "100 sayfa okuduğun için Allah mükafatını versin.",
            icon = "📖",
            category = BadgeModel.BadgeCategory.PROGRESS,
            unlockCondition = "100 sayfa oku"
        ),
        BadgeModel(
            id = ProgressManager.BADGE_FIRST_HATIM,
            name = "İlk Hatim",
            description = "İlk hatmini tamamladığın için Allah kabul etsin.",
            icon = "🕌",
            category = BadgeModel.BadgeCategory.PROGRESS,
            unlockCondition = "İlk hatmini tamamla"
        ),
        
        // Seri Rozetleri
        BadgeModel(
            id = ProgressManager.BADGE_7_DAY_STREAK,
            name = "İstikrarlı Yolcu",
            description = "7 gün boyunca okumaya devam ettiğin için Allah razı olsun.",
            icon = "🌿",
            category = BadgeModel.BadgeCategory.STREAK,
            unlockCondition = "7 gün üst üste oku"
        ),
        BadgeModel(
            id = "30_day_streak",
            name = "İstikrar Elçisi",
            description = "30 gün boyunca okumaya devam ettiğin için Allah mükafatını versin.",
            icon = "🤲",
            category = BadgeModel.BadgeCategory.STREAK,
            unlockCondition = "30 gün üst üste oku"
        ),
        BadgeModel(
            id = "morning_reader",
            name = "Sabah Yolcusu",
            description = "Sabah namazından sonra okumaya başladığın için Allah razı olsun.",
            icon = "🌅",
            category = BadgeModel.BadgeCategory.STREAK,
            unlockCondition = "Sabah okuma alışkanlığı kazan"
        ),
        
        // Premium Rozetler
        BadgeModel(
            id = "premium_guide",
            name = "Yol Gösterici",
            description = "Premium üye olarak Kur'an okumaya devam ettiğin için Allah razı olsun.",
            icon = "📿",
            category = BadgeModel.BadgeCategory.PREMIUM,
            isPremium = true,
            unlockCondition = "Premium üye ol"
        ),
        BadgeModel(
            id = "premium_ramadan",
            name = "Ramazan Hatmi",
            description = "Ramazan ayında hatim tamamladığın için Allah kabul etsin.",
            icon = "🌙",
            category = BadgeModel.BadgeCategory.PREMIUM,
            isPremium = true,
            unlockCondition = "Ramazan'da hatim tamamla"
        ),
        BadgeModel(
            id = "premium_3_hatim",
            name = "Üç Hatim",
            description = "Üç hatim tamamladığın için Allah mükafatını versin.",
            icon = "🕋",
            category = BadgeModel.BadgeCategory.PREMIUM,
            isPremium = true,
            unlockCondition = "3 hatim tamamla"
        )
    )
    
    /**
     * Açılmış rozetleri getir - O(1) lookup
     */
    fun getUnlockedBadges(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val badgesJson = prefs.getString(KEY_UNLOCKED_BADGES, "[]") ?: "[]"
        return parseBadges(badgesJson)
    }
    
    /**
     * Rozet açıldı mı kontrol - O(1)
     */
    fun isBadgeUnlocked(context: Context, badgeId: String): Boolean {
        return getUnlockedBadges(context).contains(badgeId)
    }
    
    /**
     * Rozet aç - hafif kayıt
     */
    fun unlockBadge(context: Context, badgeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlocked = getUnlockedBadges(context).toMutableSet()
        
        if (!unlocked.contains(badgeId)) {
            unlocked.add(badgeId)
            prefs.edit()
                .putString(KEY_UNLOCKED_BADGES, unlocked.joinToString(",", "[", "]"))
                .apply()
        }
    }
    
    /**
     * Rozet gösterildi mi kontrol (tekrar gösterme için)
     */
    fun isBadgeShown(context: Context, badgeId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shownJson = prefs.getString(KEY_SHOWN_BADGES, "[]") ?: "[]"
        return parseBadges(shownJson).contains(badgeId)
    }
    
    /**
     * Rozet gösterildi olarak işaretle
     */
    fun markBadgeAsShown(context: Context, badgeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shown = parseBadges(prefs.getString(KEY_SHOWN_BADGES, "[]") ?: "[]").toMutableSet()
        shown.add(badgeId)
        prefs.edit()
            .putString(KEY_SHOWN_BADGES, shown.joinToString(",", "[", "]"))
            .apply()
    }
    
    /**
     * Premium kontrolü - hafif, local state
     */
    fun isPremiumUser(context: Context): Boolean {
        // Mevcut premium kontrolünü kullan (eğer varsa)
        // Yoksa Firebase'den kontrol et ama cache'le
        return try {
            val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val cachedPremium = prefs.getBoolean("isPremium", false)
            if (cachedPremium) return true
            
            // Firebase'den kontrol (cache'lenmiş)
            FirebaseAuth.getInstance().currentUser?.let { user ->
                // Premium kontrolü burada yapılabilir
                // Şimdilik false, gerçek kontrol eklenecek
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Eski kullanıcılar için tek seferlik migration + mevcut ilerlemeye göre rozet açma.
     * Profil açıldığında veya rozet ekranı yüklendiğinde çağrılmalı; böylece 0 rozet görünmez.
     */
    fun syncBadgesFromExistingProgress(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BADGE_MIGRATION_DONE, false)) {
            checkAndUnlockBadges(context)
            return
        }
        // Eski ProgressManager rozetlerini BadgeManager'a kopyala
        val legacyBadges = ProgressManager.getBadges(context)
        if (legacyBadges.isNotEmpty()) {
            var unlocked = getUnlockedBadges(context).toMutableSet()
            for (id in legacyBadges) {
                if (!unlocked.contains(id)) unlocked.add(id)
            }
            prefs.edit()
                .putString(KEY_UNLOCKED_BADGES, unlocked.joinToString(",", "[", "]"))
                .apply()
        }
        // Mevcut istatistiklere göre eksik rozetleri aç
        checkAndUnlockBadges(context)
        prefs.edit().putBoolean(KEY_BADGE_MIGRATION_DONE, true).apply()
    }
    
    /**
     * Rozet kontrolü ve unlock - performans odaklı.
     * İlk cüz, 100 sayfa, 7/30 gün seri, ilk hatim vb. mevcut veriye göre de açılır (eski kullanıcı uyumu).
     */
    fun checkAndUnlockBadges(context: Context) {
        var unlocked = getUnlockedBadges(context)
        val streakDays = ProgressManager.getStreakDays(context)
        val totalPages = ProgressManager.getTotalPagesRead(context)
        val isPremium = isPremiumUser(context)
        val userId = getUserId(context)
        val userPrefs = context.getSharedPreferences("UserGoal_$userId", Context.MODE_PRIVATE)
        val completedHatims = userPrefs.getInt("completedHatims", 0)
        
        // İlk cüz (~20 sayfa)
        if (totalPages >= 20 && !unlocked.contains(ProgressManager.BADGE_FIRST_JUZ)) {
            unlockBadge(context, ProgressManager.BADGE_FIRST_JUZ)
            unlocked = getUnlockedBadges(context)
        }
        // 100 sayfa
        if (totalPages >= 100 && !unlocked.contains(ProgressManager.BADGE_100_PAGES)) {
            unlockBadge(context, ProgressManager.BADGE_100_PAGES)
            unlocked = getUnlockedBadges(context)
        }
        // 7 gün seri
        if (streakDays >= 7 && !unlocked.contains(ProgressManager.BADGE_7_DAY_STREAK)) {
            unlockBadge(context, ProgressManager.BADGE_7_DAY_STREAK)
            unlocked = getUnlockedBadges(context)
        }
        // 30 gün seri
        if (streakDays >= 30 && !unlocked.contains("30_day_streak")) {
            unlockBadge(context, "30_day_streak")
            unlocked = getUnlockedBadges(context)
        }
        // İlk hatim (Firebase'den ProfileFragment'ta completedHatims yazılıyor)
        if (completedHatims >= 1 && !unlocked.contains(ProgressManager.BADGE_FIRST_HATIM)) {
            unlockBadge(context, ProgressManager.BADGE_FIRST_HATIM)
            unlocked = getUnlockedBadges(context)
        }
        // Premium: 3 hatim
        if (isPremium && completedHatims >= 3 && !unlocked.contains("premium_3_hatim")) {
            unlockBadge(context, "premium_3_hatim")
        }
    }
    
    private fun getUserId(context: Context): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        } catch (e: Exception) {
            "guest"
        }
    }
    
    /**
     * Toplam açılmış rozet sayısı - O(1)
     */
    fun getUnlockedCount(context: Context): Int {
        return getUnlockedBadges(context).size
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
