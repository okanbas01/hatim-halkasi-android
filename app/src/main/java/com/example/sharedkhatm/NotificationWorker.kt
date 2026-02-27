package com.example.sharedkhatm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Locale
import kotlin.random.Random

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Bildirimin hazırlanıyor…")
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val channelId = "hh_worker_fg" // ✅ prayer kanalından AYRI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Arka Plan İşlemleri",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }

        val n = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Hatim Halkası")
            .setContentText(progress)
            // ✅ Foreground notification small icon: BEYAZ SVG
            .setSmallIcon(R.drawable.ic_notification_app)
            .setOngoing(true)
            .build()

        return ForegroundInfo(999, n)
    }

    override fun doWork(): Result {
        val type = inputData.getString("type") ?: "prayer"
        val prayerNameRaw = inputData.getString("prayerName")?.takeIf { it.isNotBlank() }

        val soundType = inputData.getInt("soundType", 1)
        val notificationId = inputData.getInt("notificationId", Random.nextInt(1000, 9999))

        val forcedTitle = inputData.getString("title").orEmpty()
        val forcedMessage = inputData.getString("message").orEmpty()

        val (title, message, extra) = if (forcedTitle.isNotBlank() || forcedMessage.isNotBlank()) {
            Triple(
                if (forcedTitle.isNotBlank()) forcedTitle else "Hatim Halkası",
                forcedMessage,
                ""
            )
        } else {
            buildContent(type, prayerNameRaw)
        }

        sendRichNotification(
            title = title,
            message = message,
            extra = extra,
            notificationId = notificationId,
            soundType = soundType
        )

        return Result.success()
    }

    private fun buildContent(type: String, prayerName: String?): Triple<String, String, String> {
        return when (type.lowercase(Locale("tr", "TR"))) {

            "morning" -> {
                val (t, m, e) = morningText()
                Triple(t, m, e)
            }

            "goal" -> {
                val (t, m, e) = goalText()
                Triple(t, m, e)
            }

            "kerahat" -> Triple(
                "Kerahat Uyarısı",
                "Akşam namazına yaklaşık 45 dk kaldı.",
                pickOne(KERAHAT_EXTRAS)
            )

            "prewarning" -> {
                val p = normalizePrayerName(prayerName)
                val (t, m, e) = preWarningText(p)
                Triple(t, m, e)
            }

            else -> {
                val p = normalizePrayerName(prayerName)
                val (t, m, e) = prayerText(p)
                Triple(t, m, e)
            }
        }
    }

    private fun morningText(): Triple<String, String, String> {
        val title = "Günün Sözü • Sabah"
        val msg = pickOne(MORNING_MESSAGES)
        val extra = pickOne(MORNING_EXTRAS)
        return Triple(title, msg, extra)
    }

    private fun goalText(): Triple<String, String, String> {
        val title = "Hedef Hatırlatıcı"
        val msg = pickOne(GOAL_MESSAGES)
        val extra = pickOne(GOAL_EXTRAS)
        return Triple(title, msg, extra)
    }

    private fun prayerText(prayer: String): Triple<String, String, String> {
        return when (prayer) {
            "SABAH" -> Triple("Vakit Girdi • Sabah", "Sabah namazı vakti girdi. Haydi namaza 🌿", "Essalatu hayrun minen nevm.")
            "ÖĞLE" -> Triple("Vakit Girdi • Öğle", "Öğle namazı vakti. Kısa bir ara…", "Secde, kalbin dinlenmesidir.")
            "İKİNDİ" -> Triple("Vakit Girdi • İkindi", "İkindi vakti girdi. Haydi namaza 🌿", "Koşturmacada bir nefes: secde.")
            "AKŞAM" -> Triple("Vakit Girdi • Akşam", "Akşam vakti girdi. Şükürle kapanış 🌙", "Günü şükürle kapatma vakti.")
            "YATSI" -> Triple("Vakit Girdi • Yatsı", "Yatsı vakti girdi. Huzur secdede 🌙", "Gecenin huzuru secde ile başlar.")
            else -> Triple("Vakit Girdi", "Namaz vakti girdi. Haydi namaza 🌿", "Rabbim kabul etsin.")
        }
    }

    private fun preWarningText(prayer: String): Triple<String, String, String> {
        val base = when (prayer) {
            "SABAH" -> "Sabah namazına 15 dk kaldı."
            "ÖĞLE" -> "Öğle namazına 15 dk kaldı."
            "İKİNDİ" -> "İkindi namazına 15 dk kaldı."
            "AKŞAM" -> "Akşam namazına 15 dk kaldı."
            "YATSI" -> "Yatsı namazına 15 dk kaldı."
            else -> "Namaz vaktine 15 dk kaldı."
        }
        val extra = pickOne(
            listOf(
                "Abdestini tazele, niyetini tazele.",
                "Hazırlan, vakit yaklaşırken kalp de toparlanır.",
                "Bir hatırlatma: huzur secdede."
            )
        )
        return Triple("Vakit Öncesi Uyarı", base, extra)
    }

    private fun normalizePrayerName(prayerName: String?): String {
        if (prayerName.isNullOrBlank()) return ""
        val p = prayerName.lowercase(Locale("tr", "TR"))
        return when {
            p.contains("sabah") || p.contains("fajr") || p.contains("imsak") -> "SABAH"
            p.contains("öğle") || p.contains("ogle") || p.contains("dhuhr") -> "ÖĞLE"
            p.contains("ikindi") || p.contains("asr") -> "İKİNDİ"
            p.contains("akşam") || p.contains("aksam") || p.contains("maghrib") -> "AKŞAM"
            p.contains("yatsı") || p.contains("yatsi") || p.contains("isha") -> "YATSI"
            else -> ""
        }
    }

    private fun sendRichNotification(
        title: String,
        message: String,
        extra: String,
        notificationId: Int,
        soundType: Int
    ) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = when (soundType) {
            2 -> "prayer_channel_adhan_v2"
            1 -> "prayer_channel_beep_v2"
            else -> "prayer_channel_silent_v2"
        }

        val soundUri: Uri? = when (soundType) {
            2 -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${applicationContext.packageName}/${R.raw.adhan}")
            1 -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${applicationContext.packageName}/${R.raw.beep}")
            else -> null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = when (soundType) {
                2 -> "Ezan Bildirimleri"
                1 -> "Uyarı Bildirimleri"
                else -> "Sessiz Bildirimler"
            }
            val importance =
                if (soundType == 0) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH

            val ch = NotificationChannel(channelId, channelName, importance).apply {
                description = "Hatim Halkası bildirimleri"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(soundType != 0)

                if (soundUri != null) {
                    val aa = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(soundUri, aa)
                } else {
                    setSound(null, null)
                }
            }
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(applicationContext, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = if (extra.isBlank()) message else "$message\n\n$extra"
        val colorPrimary = ContextCompat.getColor(applicationContext, R.color.primary_green)

        // ✅ Bildirim içi renkli logo
        val largeIconBitmap = BitmapFactory.decodeResource(
            applicationContext.resources,
            R.mipmap.ic_launcher_round
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            // ✅ Small icon: BEYAZ SVG (status bar)
            .setSmallIcon(R.drawable.ic_notification_app)
            // ✅ Large icon: RENKLİ LOGO (notification content)
            .setLargeIcon(largeIconBitmap)
            .setColor(colorPrimary)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (soundType == 0) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)

        if (soundUri != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun pickOne(list: List<String>): String = list[Random.nextInt(list.size)]

    companion object {

        private val MORNING_MESSAGES = listOf(
            "Hayırlı sabahlar 🌿 Bugünün sözünü okumayı unutma.",
            "Güne Bismillah… Bugün kısa bir okuma bile berekettir.",
            "Bugün de hayırla başlasın. Kalbine iyi gelecek bir söz var."
        )

        private val MORNING_EXTRAS = listOf(
            "Küçük adımlar büyük alışkanlıklar doğurur.",
            "Allah’ım bugünümü hayırlı eyle.",
            "Bir dakika bile olsa kalbini dinle."
        )

        private val GOAL_MESSAGES = listOf(
            "Bugünkü hedefin için hâlâ vaktin var. Hadi gayret 💪",
            "Hedefine az kaldı. Küçük bir adım bile çok kıymetli 🌿",
            "İstikrar, niyetin en güzel meyvesidir. Devam 💪"
        )

        private val GOAL_EXTRAS = listOf(
            "Az da olsa devamlı olan daha kıymetlidir.",
            "Rabbim kolaylaştırsın.",
            "Niyetin güzel, yolun açık olsun."
        )

        private val KERAHAT_EXTRAS = listOf(
            "Vakit yaklaşırken hazırlık berekettir.",
            "Kısa bir hatırlatma: kalp huzuru secdede bulur.",
            "Abdestini tazele, niyetini tazele."
        )
    }
}
