package com.example.sharedkhatm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_MORNING
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Hatim Halkası"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val soundType = intent.getIntExtra(EXTRA_SOUND_TYPE, 1)

        // Daily bildirimlerde prayerName yok; boş gönderiyoruz.
        NotificationHelper.sendNow(
            context = context,
            type = type,                 // "morning" | "goal"
            prayerName = null,
            title = title,
            message = message,
            notificationId = notificationId,
            soundType = soundType
        )

        // Not: Exact alarm ile setExact kurduğun için her tetikte tekrar kurmaya gerek yok.
        // Eğer "setRepeating" kullanırsan burada tekrar schedule yapılır.
    }

    companion object {
        // Scheduler'ın koyduğu extra'lar
        const val EXTRA_TYPE = "type"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_SOUND_TYPE = "soundType"

        // DailyAlarmScheduler'ın kullanacağı type değerleri
        const val TYPE_MORNING = "morning"
        const val TYPE_GOAL = "goal"
    }
}
