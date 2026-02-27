package com.example.sharedkhatm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object PrayerAlarmScheduler {

    fun scheduleExact(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        title: String,
        message: String,
        notificationId: Int,
        soundType: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("notificationId", notificationId)
            putExtra("soundType", soundType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 12+ exact alarm izni gerekebilir (manifestte ekleyeceğiz)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            // fallback: exact olmazsa bile kur
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    fun cancelAllPrayerAlarms(context: Context) {

        val names = listOf(
            "Sabah",
            "Öğle",
            "İkindi",
            "Akşam",
            "Yatsı"
        )

        // Tam vakit + 15 dk önce
        for (name in names) {
            cancel(context, "exact_$name".hashCode())
            cancel(context, "pre_$name".hashCode())
        }

        // Kerahat
        cancel(context, "kerahat".hashCode())

        // WorkManager tarafını da temizle
        val wm = androidx.work.WorkManager.getInstance(context)
        wm.cancelAllWorkByTag("prayer_alarm")
        wm.cancelAllWorkByTag("exact_prayer")
        wm.cancelAllWorkByTag("pre_warning")
    }

}
