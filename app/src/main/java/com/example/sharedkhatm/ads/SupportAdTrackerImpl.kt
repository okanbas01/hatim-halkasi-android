package com.example.sharedkhatm.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Locale

private val Context.supportAdDataStore: DataStore<Preferences> by preferencesDataStore(name = "support_ad_tracker")

private val KEY_DATE = stringPreferencesKey("support_date")
private val KEY_COUNT = intPreferencesKey("support_count")
private val KEY_LAST_REWARD_MS = longPreferencesKey("support_last_reward_ms")
private val KEY_HOUR_WINDOW = stringPreferencesKey("support_hour_window")
private val KEY_COUNT_THIS_HOUR = intPreferencesKey("support_count_this_hour")

/**
 * Günlük/saatlik destek sayacı. Limitler Remote Config (AdRemoteConfig) üzerinden.
 * Bot algısı: max 5/gün, 2/saat, min 5 dk aralık.
 */
class SupportAdTrackerImpl(private val context: Context) : SupportAdTracker {

    private val dataStore: DataStore<Preferences>
        get() = context.applicationContext.supportAdDataStore

    @Volatile private var cachedDate: String = ""
    @Volatile private var cachedCount: Int = 0
    @Volatile private var cachedLastRewardMs: Long = 0L
    @Volatile private var cachedHourWindow: String = ""
    @Volatile private var cachedCountThisHour: Int = 0
    @Volatile private var initialized: Boolean = false

    private fun ensureLoaded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runBlocking(Dispatchers.IO) {
                dataStore.data.map { prefs ->
                    cachedDate = prefs[KEY_DATE] ?: todayKey()
                    cachedCount = prefs[KEY_COUNT] ?: 0
                    cachedLastRewardMs = prefs[KEY_LAST_REWARD_MS] ?: 0L
                    cachedHourWindow = prefs[KEY_HOUR_WINDOW] ?: hourKey()
                    cachedCountThisHour = prefs[KEY_COUNT_THIS_HOUR] ?: 0
                    if (cachedDate != todayKey()) {
                        cachedDate = todayKey()
                        cachedCount = 0
                        cachedHourWindow = hourKey()
                        cachedCountThisHour = 0
                    }
                    if (cachedHourWindow != hourKey()) {
                        cachedHourWindow = hourKey()
                        cachedCountThisHour = 0
                    }
                }.first()
            }
            initialized = true
        }
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance(Locale.getDefault())
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun hourKey(): String {
        val c = Calendar.getInstance(Locale.getDefault())
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}-${c.get(Calendar.HOUR_OF_DAY)}"
    }

    override fun getTodayCount(): Int {
        ensureLoaded()
        if (cachedDate != todayKey()) {
            cachedDate = todayKey()
            cachedCount = 0
            cachedHourWindow = hourKey()
            cachedCountThisHour = 0
        }
        if (cachedHourWindow != hourKey()) {
            cachedHourWindow = hourKey()
            cachedCountThisHour = 0
        }
        val maxDay = AdRemoteConfig.rewardedMaxPerDay()
        return cachedCount.coerceIn(0, maxDay)
    }

    override fun canSupport(): Boolean {
        ensureLoaded()
        val now = System.currentTimeMillis()
        if (cachedDate != todayKey()) {
            cachedDate = todayKey()
            cachedCount = 0
            cachedHourWindow = hourKey()
            cachedCountThisHour = 0
        }
        if (cachedHourWindow != hourKey()) {
            cachedHourWindow = hourKey()
            cachedCountThisHour = 0
        }
        val maxDay = AdRemoteConfig.rewardedMaxPerDay()
        val maxHour = AdRemoteConfig.rewardedMaxPerHour()
        val minIntervalMs = AdRemoteConfig.rewardedMinIntervalMinutes() * 60L * 1000L
        if (cachedCount >= maxDay) return false
        if (cachedCountThisHour >= maxHour) return false
        if (cachedLastRewardMs > 0 && (now - cachedLastRewardMs) < minIntervalMs) return false
        return true
    }

    override fun increment() {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val today = todayKey()
        val hour = hourKey()
        if (cachedDate != today) {
            cachedDate = today
            cachedCount = 0
            cachedHourWindow = hour
            cachedCountThisHour = 0
        }
        if (cachedHourWindow != hour) {
            cachedHourWindow = hour
            cachedCountThisHour = 0
        }
        cachedLastRewardMs = now
        cachedCount = (cachedCount + 1).coerceAtMost(AdRemoteConfig.rewardedMaxPerDay())
        cachedCountThisHour = (cachedCountThisHour + 1).coerceAtMost(AdRemoteConfig.rewardedMaxPerHour())
        val date = cachedDate
        val count = cachedCount
        val lastMs = cachedLastRewardMs
        val hourWindow = cachedHourWindow
        val countHour = cachedCountThisHour
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            dataStore.edit {
                it[KEY_DATE] = date
                it[KEY_COUNT] = count
                it[KEY_LAST_REWARD_MS] = lastMs
                it[KEY_HOUR_WINDOW] = hourWindow
                it[KEY_COUNT_THIS_HOUR] = countHour
            }
        }
    }
}
