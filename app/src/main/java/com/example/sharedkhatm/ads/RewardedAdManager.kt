package com.example.sharedkhatm.ads

import android.app.Activity

/**
 * Rewarded "Destekçi" reklamı. Ödül = reklamsızlık değil; SupportAdTracker.increment().
 * DI uyumlu.
 */
interface RewardedAdManager {

    /** Uygulama açılışında / izleme sonrası arka planda çağrılır. */
    fun preloadRewarded()

    /**
     * Rewarded göster. İzlenirse onRewarded (içerde SupportAdTracker.increment()), kapatılırsa onDismissed.
     * Ad hazır değilse preload tetiklenir ve onDismissed çağrılır; crash olmaz.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit)
}
