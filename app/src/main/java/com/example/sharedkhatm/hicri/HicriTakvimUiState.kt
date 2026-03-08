package com.example.sharedkhatm.hicri

/**
 * Hicri takvim ekranları için UI state (MVVM).
 */
sealed class HicriCardState {
    object Loading : HicriCardState()
    data class Success(
        val todayHijri: String,
        val todayGregorian: String,
        val periodTitle: String?,
        val periodRange: String?,
        val nextSpecialDay: HicriSpecialDayItem?
    ) : HicriCardState()
    data class Error(val message: String) : HicriCardState()
}

sealed class HicriYearListState {
    object Loading : HicriYearListState()
    data class Success(val year: Int, val items: List<HicriSpecialDayItem>) : HicriYearListState()
    data class Error(val message: String) : HicriYearListState()
}
