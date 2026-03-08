package com.example.sharedkhatm.ads

/** Günlük destek sayacı. Rewarded = destek; günde max 3. DI uyumlu. */
interface SupportAdTracker {
    fun getTodayCount(): Int
    fun canSupport(): Boolean
    fun increment()
}
