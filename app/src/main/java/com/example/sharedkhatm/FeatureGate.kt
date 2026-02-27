package com.example.sharedkhatm

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

object FeatureGate {

    data class GateState(
        val enabled: Boolean,
        val message: String
    )

    private const val RC_ENABLED = "feature_gonul_rehberi_enabled"
    private const val RC_MESSAGE = "feature_gonul_rehberi_message"

    private const val FS_COLLECTION = "app_settings"
    private const val FS_DOC = "features"
    private const val FS_FIELD_ENABLED = "gonulRehberiEnabled"
    private const val FS_FIELD_MESSAGE = "gonulRehberiMessage"

    private val fs by lazy { FirebaseFirestore.getInstance() }

    private val rc: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val minInterval = if (BuildConfig.DEBUG) 0L else 3600L

            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(minInterval)
                    .build()
            )

            val defaults: Map<String, Any> = mapOf(
                RC_ENABLED to true,
                RC_MESSAGE to "Bu özellik şu an bakımda."
            )
            setDefaultsAsync(defaults)
        }
    }

    @Volatile private var cached: GateState? = null

    // 🔥 ANR FIX – artık her zaman IO thread
    suspend fun refreshRemoteConfig() {
        try {
            withContext(Dispatchers.IO) {
                rc.fetchAndActivate().await()
            }
        } catch (_: Throwable) { }
    }

    private fun getRemoteConfigState(): GateState {
        val enabled = rc.getBoolean(RC_ENABLED)
        val msg = rc.getString(RC_MESSAGE).ifBlank { "Bu özellik şu an bakımda." }
        return GateState(enabled = enabled, message = msg)
    }

    fun getState(): GateState = cached ?: getRemoteConfigState()

    fun listenKillSwitch(onUpdate: (GateState) -> Unit): ListenerRegistration {
        onUpdate(getState())

        return fs.collection(FS_COLLECTION)
            .document(FS_DOC)
            .addSnapshotListener { snap, _ ->
                val rcState = getRemoteConfigState()

                val fsEnabled = if (snap != null && snap.exists()) {
                    snap.getBoolean(FS_FIELD_ENABLED) ?: rcState.enabled
                } else rcState.enabled

                val fsMsg = if (snap != null && snap.exists()) {
                    snap.getString(FS_FIELD_MESSAGE).orEmpty()
                } else ""

                val enabledFinal = rcState.enabled && fsEnabled
                val msgFinal = when {
                    fsMsg.isNotBlank() -> fsMsg
                    rcState.message.isNotBlank() -> rcState.message
                    else -> "Bu özellik şu an bakımda."
                }

                val state = GateState(enabledFinal, msgFinal)
                cached = state
                onUpdate(state)
            }
    }
}
