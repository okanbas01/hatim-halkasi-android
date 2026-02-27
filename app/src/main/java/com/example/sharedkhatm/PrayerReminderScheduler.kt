package com.example.sharedkhatm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.Locale

/**
 * Ezan bildirimi: AlarmManager ile kesin zaman, tek seferlik schedule, arka planda sürekli çalışan servis yok.
 * - setExactAndAllowWhileIdle (M+), setExact (KITKAT+). Android 12+ canScheduleExactAlarms kontrolü.
 * - Her vakit için ayrı alarm. Geçmiş vakitler atlanır, sadece gelecek schedule edilir.
 * - Alarm ateşlenince PrayerAlarmReceiver içinde rescheduleAll ile sonraki gün kurulur.
 * - BOOT/TIME_SET/TIMEZONE_CHANGED → PrayerRescheduleReceiver yeniden kurar.
 */
class PrayerReminderScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val appPrefs =
        context.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)

    private val locPrefs =
        context.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)

    fun rescheduleAll(timings: PrayerTimesLite) {

        // 1) Şehir/konum yok → hiçbir şey kurma
        if (!locPrefs.contains("savedCity")) {
            cancelAll()
            return
        }

        // 2) Android 12+ exact alarm izni yok → kurma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            cancelAll()
            return
        }

        // 3) Önce tüm eski alarmları temizle
        cancelAll()

        val now = System.currentTimeMillis()

        val preWarningEnabled = appPrefs.getBoolean("notif_prayer", true)
        val kerahatEnabled = appPrefs.getBoolean("notif_kerahat", false)

        val prayers = listOf(
            "Sabah" to timings.fajr,
            "Öğle" to timings.dhuhr,
            "İkindi" to timings.asr,
            "Akşam" to timings.maghrib,
            "Yatsı" to timings.isha
        )

        prayers.forEach { (name, timeRaw) ->
            val prayerMillis = parseMillis(timeRaw, now) ?: return@forEach

            val soundType = appPrefs.getInt(soundKey(name), 1)

            // A) TAM VAKİT — sadece gelecek vakitler
            if (soundType != 0) {
                if (schedule(
                    triggerAt = prayerMillis,
                    requestCode = rcExact(name),
                    type = "prayer",
                    prayerName = name,
                    notificationId = nidExact(name),
                    soundType = soundType
                )) {
                    Log.d("PrayerDebug", "Scheduled $name at: ${formatTimeForLog(prayerMillis)}")
                }
            }

            // B) 15 DK ÖNCE (sadece ses açıksa)
            if (preWarningEnabled && soundType != 0) {
                val warnAt = prayerMillis - 15 * 60 * 1000L
                if (warnAt > now) {
                    if (schedule(
                        triggerAt = warnAt,
                        requestCode = rcPre(name),
                        type = "prewarning",
                        prayerName = name,
                        notificationId = nidPre(name),
                        soundType = 1
                    )) {
                        Log.d("PrayerDebug", "Scheduled $name (15 min before) at: ${formatTimeForLog(warnAt)}")
                    }
                }
            }

            // C) KERAHAT (SADECE AKŞAM)
            if (name == "Akşam" && kerahatEnabled) {
                val kerahatAt = prayerMillis - 45 * 60 * 1000L
                if (kerahatAt > now) {
                    if (schedule(
                        triggerAt = kerahatAt,
                        requestCode = rcKerahat(),
                        type = "kerahat",
                        prayerName = name,
                        notificationId = 9999,
                        soundType = 1
                    )) {
                        Log.d("PrayerDebug", "Scheduled kerahat at: ${formatTimeForLog(kerahatAt)}")
                    }
                }
            }
        }
    }

    fun cancelAll() {
        listOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı").forEach {
            cancel(rcExact(it))
            cancel(rcPre(it))
        }
        cancel(rcKerahat())
    }

    /** Schedules only if triggerAt is in the future (with 1s margin). Returns true if scheduled. */
    private fun schedule(
        triggerAt: Long,
        requestCode: Int,
        type: String,
        prayerName: String,
        notificationId: Int,
        soundType: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        if (triggerAt <= now || triggerAt - now < MIN_TRIGGER_MARGIN_MS) return false
        val pi = pendingIntent(requestCode, type, prayerName, notificationId, soundType)

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ->
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            return true
        } catch (_: SecurityException) {
            cancel(requestCode)
            return false
        }
    }

    private fun formatTimeForLog(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun cancel(requestCode: Int) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(
        requestCode: Int,
        type: String,
        prayerName: String,
        notificationId: Int,
        soundType: Int
    ): PendingIntent {

        val title = "Hatim Halkası"
        val message = buildMessage(type, prayerName)

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra("type", type)
            putExtra("prayerName", prayerName)
            putExtra("notificationId", notificationId)
            putExtra("soundType", soundType)

            // ✅ BUNLAR YOKTU -> BOŞ BİLDİRİM/SESSİZLİK BURADAN GELİYORDU
            putExtra("title", title)
            putExtra("message", message)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ✅ Mesaj üretici
    private fun buildMessage(type: String, prayerName: String): String {
        return when (type) {
            "prewarning" -> "$prayerName vaktine 15 dakika kaldı."
            "kerahat" -> "Akşam vaktine 45 dakika kaldı (kerahat)."
            else -> "$prayerName vakti girdi."
        }
    }


    /** Vakit string (HH:mm) → bugün/yarın local timezone'da milisaniye. Geçmişse yarın. */
    private fun parseMillis(timeRaw: String?, now: Long): Long? {
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
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    private fun soundKey(prayerName: String): String = when (prayerName) {
        "Sabah" -> "sound_fajr"
        "Öğle" -> "sound_dhuhr"
        "İkindi" -> "sound_asr"
        "Akşam" -> "sound_maghrib"
        "Yatsı" -> "sound_isha"
        else -> "sound_fajr"
    }

    private fun rcExact(name: String) = 10000 + safeMod(name.hashCode(), 1000)
    private fun rcPre(name: String) = 11000 + safeMod(name.hashCode(), 1000)
    private fun rcKerahat() = 12001

    private fun nidExact(name: String) = 20000 + safeMod(name.hashCode(), 1000)
    private fun nidPre(name: String) = 21000 + safeMod(name.hashCode(), 1000)

    private fun safeMod(x: Int, m: Int): Int {
        val r = x % m
        return if (r < 0) r + m else r
    }

    companion object {
        private const val ACTION_ALARM = "com.example.sharedkhatm.PRAYER_ALARM"
        private const val MIN_TRIGGER_MARGIN_MS = 1000L
    }
}
