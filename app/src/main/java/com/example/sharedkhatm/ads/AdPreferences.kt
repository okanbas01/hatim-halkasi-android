package com.example.sharedkhatm.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.adDataStore: DataStore<Preferences> by preferencesDataStore(name = "ad_prefs")
private val KEY_IS_PREMIUM = booleanPreferencesKey("is_premium")

/**
 * Premium flag. Premium'da banner ve rewarded gösterilmez.
 * Reklamsızlık ödülü yok; sadece destek sayacı (SupportAdTracker).
 */
object AdPreferences {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var dataStore: DataStore<Preferences>? = null
    @Volatile private var cachedIsPremium: Boolean = false
    @Volatile private var initialized: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            dataStore = context.applicationContext.adDataStore
            runBlocking(Dispatchers.IO) {
                dataStore?.data?.map { prefs ->
                    cachedIsPremium = prefs[KEY_IS_PREMIUM] ?: false
                }?.first() ?: Unit
            }
            initialized = true
        }
    }

    fun isPremium(): Boolean = cachedIsPremium
    fun shouldShowAds(): Boolean = !cachedIsPremium

    fun setPremium(value: Boolean) {
        cachedIsPremium = value
        scope.launch {
            dataStore?.edit { it[KEY_IS_PREMIUM] = value }
        }
    }
}
