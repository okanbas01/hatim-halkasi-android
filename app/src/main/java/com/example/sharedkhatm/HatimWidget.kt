package com.example.sharedkhatm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HatimWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

            val prefs = context.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
            val sFajr = prefs.getString("saved_fajr", "--:--")
            val sDhuhr = prefs.getString("saved_dhuhr", "--:--")
            val sAsr = prefs.getString("saved_asr", "--:--")
            val sMaghrib = prefs.getString("saved_maghrib", "--:--")
            val sIsha = prefs.getString("saved_isha", "--:--")
            val sSunrise = prefs.getString("saved_sunrise", "--:--")

            val (nextName, nextTime, remainingText) = getNextPrayerAndRemaining(sFajr, sSunrise, sDhuhr, sAsr, sMaghrib, sIsha)

            val globalPrefs = context.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
            val lastReadName = globalPrefs.getString("lastReadName", "Henüz Yok")

            val views = RemoteViews(context.packageName, R.layout.widget_prayer)

            views.setTextViewText(R.id.widgetNextName, nextName)
            views.setTextViewText(R.id.widgetNextTime, nextTime)
            views.setTextViewText(R.id.widgetCountDown, remainingText)
            views.setTextViewText(R.id.widgetHatimStatus, lastReadName)

            // DİKKAT: İkon boyama kodu (setColorFilter) TAMAMEN SİLİNDİ.
            // Artık ikon XML'deki orijinal haliyle (renkli) görünecek.

            // Tıklama Olayı
            val intent = Intent(context, HomeActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layoutNext, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // HatimWidget.kt içindeki getNextPrayerAndRemaining fonksiyonunu bununla değiştir:

        private fun getNextPrayerAndRemaining(f: String?, sun: String?, d: String?, a: String?, m: String?, i: String?): Triple<String, String, String> {
            try {
                // Saat formatı (Sadece saat ve dakika, saniye yok)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val now = Date()

                // Şu anki saati parse et (Yıl/Ay/Gün bilgisi önemli değil, saat karşılaştıracağız)
                val nowStr = sdf.format(now)
                val nowParsed = sdf.parse(nowStr) ?: return Triple("Yükleniyor", "--:--", "")

                // Vakit Listesi
                val timings = listOf(
                    "İMSAK" to f, "GÜNEŞ" to sun, "ÖĞLE" to d, "İKİNDİ" to a, "AKŞAM" to m, "YATSI" to i
                )

                for ((name, timeRaw) in timings) {
                    if (timeRaw != null) {
                        val cleanTime = timeRaw.split(" ")[0]
                        val prayerTime = sdf.parse(cleanTime)

                        // Eğer vakit şu andan sonraysa
                        if (prayerTime != null && prayerTime.after(nowParsed)) {

                            // Kalan süreyi hesaplamak için TAM TARİH kullanalım
                            val nowFull = Calendar.getInstance()
                            val prayerFull = Calendar.getInstance()

                            val timeParts = cleanTime.split(":")
                            prayerFull.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                            prayerFull.set(Calendar.MINUTE, timeParts[1].toInt())
                            prayerFull.set(Calendar.SECOND, 0)

                            // Eğer saat olarak geçtiyse ama biz hala bugündeysek (Bu blok zaten after kontrolüyle korunuyor ama garanti olsun)
                            if (prayerFull.before(nowFull)) {
                                continue
                            }

                            val diffMillis = prayerFull.timeInMillis - nowFull.timeInMillis
                            return Triple(name, cleanTime, formatDuration(diffMillis))
                        }
                    }
                }

                // Eğer hiçbir vakit kalmadıysa (Yatsıdan sonra), YARININ İMSAK vaktine bakıyoruz
                val imsakTimeStr = f?.split(" ")?.get(0) ?: "--:--"

                val nowFull = Calendar.getInstance()
                val tomorrowImsak = Calendar.getInstance()
                tomorrowImsak.add(Calendar.DAY_OF_YEAR, 1) // Yarına git

                val parts = imsakTimeStr.split(":")
                tomorrowImsak.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                tomorrowImsak.set(Calendar.MINUTE, parts[1].toInt())
                tomorrowImsak.set(Calendar.SECOND, 0)

                val diffMillis = tomorrowImsak.timeInMillis - nowFull.timeInMillis

                return Triple("İMSAK", imsakTimeStr, formatDuration(diffMillis))

            } catch (e: Exception) {
                return Triple("HATA", "--:--", "")
            }
        }

        private fun formatDuration(millis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            return "Kalan: ${hours} sa ${minutes} dk"
        }
    }
}