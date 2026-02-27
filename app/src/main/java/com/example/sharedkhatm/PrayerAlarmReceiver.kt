package com.example.sharedkhatm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Ezan bildirimi tetiklenince: bildirimi göster (sadece vakit geçtiyse), sonra sonraki gün alarmlarını yeniden kur.
 * Biriken bildirimler (uygulama açılmadan birkaç vakit geçmişse) tek özet bildirimde toplanır; kilitlenme önlenir.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val type = intent.getStringExtra("type") ?: "prayer"
        val prayerName = intent.getStringExtra("prayerName") ?: "Namaz"

        val lite = PrayerTimesCache.read(context) ?: readLiteFromLocationPrefs(context)

        if (lite != null && !shouldFireNotification(lite, type, prayerName)) {
            Log.d("PrayerDebug", "Skipping notification: $prayerName ($type) — prayer time not yet reached")
            rescheduleOnBackground(context, lite)
            return
        }

        // Biriken vakitler: Birden fazla namaz vakti geçmişse tek özet bildirim göster, diğerlerini gösterme
        if (lite != null && type == "prayer") {
            val pastPrayers = getPastPrayerNamesToday(lite)
            if (pastPrayers.size >= 2) {
                if (shouldShowBatchNotification(context)) {
                    val message = pastPrayers.joinToString(", ") { it } + " namaz vakitleri girdi."
                    NotificationHelper.sendNow(
                        context = context,
                        type = "prayer",
                        prayerName = null,
                        title = "Hatim Halkası",
                        message = message,
                        notificationId = BATCH_NOTIFICATION_ID,
                        soundType = intent.getIntExtra("soundType", 1)
                    )
                    markBatchNotificationShown(context)
                }
                rescheduleOnBackground(context, lite)
                return
            }
            if (wasBatchJustShown(context)) {
                rescheduleOnBackground(context, lite)
                return
            }
        }

        val title = intent.getStringExtra("title") ?: "Hatim Halkası"
        val message = intent.getStringExtra("message")?.takeIf { it.isNotBlank() }
            ?: when (type) {
                "prewarning" -> "$prayerName vaktine 15 dakika kaldı."
                "kerahat" -> "Akşam vaktine 45 dakika kaldı (kerahat)."
                else -> "$prayerName vakti girdi."
            }
        val notificationId = intent.getIntExtra("notificationId", 0)
        val soundType = intent.getIntExtra("soundType", 1)

        NotificationHelper.sendNow(
            context = context,
            type = type,
            prayerName = prayerName,
            title = title,
            message = message,
            notificationId = notificationId,
            soundType = soundType
        )

        rescheduleOnBackground(context, lite)
    }

    /** Bugün vakti geçmiş namazların listesi (Sabah, Öğle, İkindi, Akşam, Yatsı). */
    private fun getPastPrayerNamesToday(lite: PrayerTimesLite): List<String> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<String>()
        val prayers = listOf(
            "Sabah" to lite.fajr,
            "Öğle" to lite.dhuhr,
            "İkindi" to lite.asr,
            "Akşam" to lite.maghrib,
            "Yatsı" to lite.isha
        )
        for ((name, timeStr) in prayers) {
            val millis = parseMillisTodayOnly(timeStr) ?: continue
            if (millis <= now) list.add(name)
        }
        return list
    }

    private fun parseMillisTodayOnly(timeRaw: String?): Long? {
        if (timeRaw.isNullOrBlank()) return null
        val clean = timeRaw.trim().split(" ")[0]
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun shouldShowBatchNotification(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_BATCH, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_BATCH_SHOWN_AT, 0L)
        val lastDay = prefs.getInt(KEY_BATCH_DAY, -1)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (lastDay != today) return true
        return System.currentTimeMillis() - last > BATCH_SUPPRESS_MS
    }

    private fun markBatchNotificationShown(context: Context) {
        context.getSharedPreferences(PREFS_BATCH, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BATCH_SHOWN_AT, System.currentTimeMillis())
            .putInt(KEY_BATCH_DAY, Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
            .apply()
    }

    private fun wasBatchJustShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_BATCH, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_BATCH_SHOWN_AT, 0L)
        val lastDay = prefs.getInt(KEY_BATCH_DAY, -1)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (lastDay != today) return false
        return System.currentTimeMillis() - last <= BATCH_SUPPRESS_MS
    }

    /** Bildirim sadece vakit (veya ön uyarı/kerahat zamanı) geçtiyse gösterilsin. */
    private fun shouldFireNotification(lite: PrayerTimesLite, type: String, prayerName: String): Boolean {
        val now = System.currentTimeMillis()
        val intendedTriggerMillis = when (type) {
            "prewarning" -> parseMillisTodayOrTomorrow(lite.timeForPrayerName(prayerName))?.minus(15 * 60 * 1000L)
            "kerahat" -> parseMillisTodayOrTomorrow(lite.maghrib)?.minus(45 * 60 * 1000L)
            else -> parseMillisTodayOrTomorrow(lite.timeForPrayerName(prayerName))
        } ?: return true
        if (now < intendedTriggerMillis - SAFETY_TOLERANCE_MS) return false
        return true
    }

    private fun parseMillisTodayOrTomorrow(timeRaw: String?): Long? {
        if (timeRaw.isNullOrBlank()) return null
        val clean = timeRaw.trim().split(" ")[0]
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    private fun rescheduleOnBackground(context: Context, lite: PrayerTimesLite?) {
        if (lite == null) return
        prayerExecutor.execute {
            try {
                PrayerReminderScheduler(context).rescheduleAll(lite)
            } catch (e: Exception) {
                Log.e("PrayerDebug", "rescheduleAll failed: ${e.message}")
            }
        }
    }

    private fun readLiteFromLocationPrefs(context: Context): PrayerTimesLite? {
        val loc = context.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        val fajr = loc.getString("saved_fajr", null) ?: return null
        val sunrise = loc.getString("saved_sunrise", "") ?: ""
        val dhuhr = loc.getString("saved_dhuhr", null) ?: return null
        val asr = loc.getString("saved_asr", "") ?: ""
        val maghrib = loc.getString("saved_maghrib", null) ?: ""
        val isha = loc.getString("saved_isha", null) ?: ""
        return PrayerTimesLite(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha
        )
    }

    companion object {
        private const val SAFETY_TOLERANCE_MS = 60 * 1000L
        private const val BATCH_NOTIFICATION_ID = 19999
        private const val PREFS_BATCH = "PrayerBatchPrefs"
        private const val KEY_BATCH_SHOWN_AT = "batch_shown_at"
        private const val KEY_BATCH_DAY = "batch_day"
        private const val BATCH_SUPPRESS_MS = 2 * 60 * 1000L // 2 dk içinde tekrar özet gösterme / tekil gösterme
        private val prayerExecutor = Executors.newSingleThreadExecutor()
    }
}

private fun PrayerTimesLite.timeForPrayerName(name: String): String? = when (name) {
    "Sabah" -> fajr
    "Öğle" -> dhuhr
    "İkindi" -> asr
    "Akşam" -> maghrib
    "Yatsı" -> isha
    else -> null
}
