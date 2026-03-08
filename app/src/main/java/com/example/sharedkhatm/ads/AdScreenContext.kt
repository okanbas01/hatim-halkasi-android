package com.example.sharedkhatm.ads

/**
 * Ekran bağlamı: Hassas ekranlarda (cenaze duası, ölümle ilgili içerik, namaz rehberi,
 * kritik manevi içerik) interstitial gösterilmez; sadece Rewarded destek gösterilebilir.
 */
enum class AdScreenContext {
    /** Normal ekran; interstitial gösterilebilir. */
    NORMAL,

    /** Hassas manevi ekran; interstitial yasak, sadece Rewarded destek. */
    SENSITIVE
}
