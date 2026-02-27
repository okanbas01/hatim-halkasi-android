package com.example.sharedkhatm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/**
 * BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED: Ezan alarmlarını yeniden kur.
 * Sürekli çalışan servis yok; sadece bu event'lerde bir kez reschedule. Ana thread bloklanmaz.
 */
class PrayerRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Tüm alarm kurulumunu arka planda yap — BroadcastReceiver 10 sn içinde bitmeli (ANR önlemi)
        val liteFromCache = PrayerTimesCache.read(context)
        val lite = liteFromCache ?: readLiteFromLocationPrefs(context)

        rescheduleExecutor.execute {
            try {
                DailyAlarmScheduler.rescheduleAll(context)
                if (lite != null) {
                    PrayerReminderScheduler(context).rescheduleAll(lite)
                }
            } catch (_: Exception) { }
        }
    }

    companion object {
        private val rescheduleExecutor = Executors.newSingleThreadExecutor()
    }

    private fun readLiteFromLocationPrefs(context: Context): PrayerTimesLite? {
        val loc = context.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)

        val fajr = loc.getString("saved_fajr", null) ?: return null
        val sunrise = loc.getString("saved_sunrise", "") ?: ""
        val dhuhr = loc.getString("saved_dhuhr", null) ?: return null
        val asr = loc.getString("saved_asr", "") ?: ""
        val maghrib = loc.getString("saved_maghrib", "") ?: ""
        val isha = loc.getString("saved_isha", "") ?: ""

        return PrayerTimesLite(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha
        )
    }
}
