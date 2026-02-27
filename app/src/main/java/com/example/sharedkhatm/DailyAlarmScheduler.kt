package com.example.sharedkhatm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Calendar

object DailyAlarmScheduler {

    private const val PREFS_GLOBAL = "AppGlobalPrefs"
    private const val KEY_NOTIF_MORNING = "notif_morning"
    private const val KEY_NOTIF_GOAL = "notif_goal"

    private fun goalPrefsName(uid: String) = "UserGoal_$uid"
    private const val KEY_DAILY_JUZ_GOAL = "dailyJuzGoal"

    private const val RC_MORNING = 50101
    private const val RC_GOAL = 50102

    private const val NID_MORNING = 1001
    private const val NID_GOAL = 1002

    private const val MORNING_HOUR = 8
    private const val GOAL_HOUR = 21

    private const val SOUND_DEFAULT = 1

    private const val TYPE_MORNING = "morning"
    private const val TYPE_GOAL = "goal"

    private const val ACTION_ALARM = "com.example.sharedkhatm.PRAYER_ALARM"

    fun scheduleMorning(context: Context) {
        scheduleDailyExact(
            context = context,
            type = TYPE_MORNING,
            hour = MORNING_HOUR,
            minute = 0,
            requestCode = RC_MORNING,
            notificationId = NID_MORNING,
            title = "Günün Sözü",
            message = buildMorningMessage(),
            soundType = SOUND_DEFAULT
        )
    }

    fun scheduleGoal(context: Context) {
        if (!hasGoalSet(context)) {
            cancelGoal(context)
            return
        }

        scheduleDailyExact(
            context = context,
            type = TYPE_GOAL,
            hour = GOAL_HOUR,
            minute = 0,
            requestCode = RC_GOAL,
            notificationId = NID_GOAL,
            title = "Hedef Hatırlatıcı",
            message = buildGoalMessage(context),
            soundType = SOUND_DEFAULT
        )
    }

    fun cancelMorning(context: Context) = cancel(context, RC_MORNING, TYPE_MORNING, NID_MORNING)
    fun cancelGoal(context: Context) = cancel(context, RC_GOAL, TYPE_GOAL, NID_GOAL)

    fun scheduleMorningIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_NOTIF_MORNING, true)

        if (!enabled || !hasNotificationPermission(context) || !canScheduleExact(context)) {
            cancelMorning(context)
            return
        }
        scheduleMorning(context)
    }

    fun scheduleGoalIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_NOTIF_GOAL, true)

        if (!enabled || !hasNotificationPermission(context) || !canScheduleExact(context) || !hasGoalSet(context)) {
            cancelGoal(context)
            return
        }
        scheduleGoal(context)
    }

    fun rescheduleAll(context: Context) {
        scheduleMorningIfEnabled(context)
        scheduleGoalIfEnabled(context)
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ---------------- internal ----------------

    private fun scheduleDailyExact(
        context: Context,
        type: String,
        hour: Int,
        minute: Int,
        requestCode: Int,
        notificationId: Int,
        title: String,
        message: String,
        soundType: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(context)) return

        val triggerAt = computeNextTrigger(hour, minute)

        val pi = buildPendingIntent(
            context = context,
            requestCode = requestCode,
            type = type,
            notificationId = notificationId,
            soundType = soundType,
            title = title,
            message = message
        )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ->
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // exact izni yoksa düşebilir
        }
    }

    private fun cancel(context: Context, requestCode: Int, type: String, notificationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(
            context = context,
            requestCode = requestCode,
            type = type,
            notificationId = notificationId,
            soundType = SOUND_DEFAULT,
            title = "",     // cancel için içerik önemli değil
            message = ""
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        type: String,
        notificationId: Int,
        soundType: Int,
        title: String,
        message: String
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_ALARM

            putExtra("type", type)                // morning | goal | prayer | prewarning | kerahat
            putExtra("prayerName", "")            // daily’de yok
            putExtra("notificationId", notificationId)
            putExtra("soundType", soundType)

            // Daily’de title/message’i ZORLA gönderiyoruz ki Worker doğru yazsın
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

    private fun computeNextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasGoalSet(context: Context): Boolean {
        val uid = FirebaseAuthProvider.uidOrNull() ?: return false
        val prefs = context.getSharedPreferences(goalPrefsName(uid), Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAILY_JUZ_GOAL, 0) > 0
    }

    private fun buildMorningMessage(): String {
        return "Hayırlı sabahlar 🌿 Bugün kalbini güzelleştirecek bir ayet/dua okumaya niyet eder misin?"
    }

    private fun buildGoalMessage(context: Context): String {
        val uid = FirebaseAuthProvider.uidOrNull() ?: return "Bugünkü hedefini tamamlamak için hâlâ vaktin var 💪"
        val prefs = context.getSharedPreferences(goalPrefsName(uid), Context.MODE_PRIVATE)
        val goal = prefs.getInt(KEY_DAILY_JUZ_GOAL, 0)
        return if (goal > 0) {
            "Bugünkü hedefin: $goal Cüz ✅ Gün bitmeden küçük bir adım bile çok kıymetli."
        } else {
            "Bugünkü hedefini tamamlamak için hâlâ vaktin var 💪"
        }
    }

    private object FirebaseAuthProvider {
        fun uidOrNull(): String? {
            return try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            } catch (_: Exception) {
                null
            }
        }
    }
}
