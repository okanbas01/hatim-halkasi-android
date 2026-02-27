package com.example.sharedkhatm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val DEBOUNCE_MS = 1000L

    @Volatile
    private var lastPrayerNotificationTime = 0L

    fun sendNow(
        context: Context,
        type: String,
        prayerName: String?,
        title: String,
        message: String,
        notificationId: Int,
        soundType: Int
    ) {
        if (notificationId <= 0) return
        if (message.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastPrayerNotificationTime < DEBOUNCE_MS) return
        lastPrayerNotificationTime = now

        val channelId = ensureChannel(context, type, soundType)

        val openIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPi = PendingIntent.getActivity(
            context,
            90000 + notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_app)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(
                if (soundType == 0) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )

        // Android 7 ve altı için (O+’ta sesi channel verir)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            when (soundType) {
                0 -> builder.setSound(null)
                1 -> builder.setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.beep}"))
                2 -> builder.setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.adhan}"))
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, builder.build())
    }

    private fun ensureChannel(context: Context, type: String, soundType: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "legacy_channel"

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ v2: eski bozuk/sessiz kanallardan kurtulmak için ID’leri versiyonladık
        val channelId = when (soundType) {
            0 -> "prayer_${type}_silent_v2"
            2 -> "prayer_${type}_adhan_v2"
            else -> "prayer_${type}_beep_v2"
        }

        if (nm.getNotificationChannel(channelId) != null) return channelId

        val name = when (soundType) {
            0 -> "Namaz Bildirimleri (Sessiz)"
            2 -> "Namaz Bildirimleri (Ezan)"
            else -> "Namaz Bildirimleri (Bip)"
        }

        val importance = when (soundType) {
            0 -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_HIGH
        }

        val channel = NotificationChannel(channelId, name, importance).apply {
            enableVibration(soundType != 0)

            when (soundType) {
                0 -> setSound(null, null)

                1 -> {
                    val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.beep}")
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(uri, attrs)
                }

                2 -> {
                    val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.adhan}")
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(uri, attrs)
                }
            }
        }

        nm.createNotificationChannel(channel)
        return channelId
    }
}
