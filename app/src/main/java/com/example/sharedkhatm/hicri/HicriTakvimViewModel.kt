package com.example.sharedkhatm.hicri

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hicri takvim MVVM ViewModel.
 * Kart (anasayfa) ve yıl listesi (HicriTakvimActivity) verisini yükler.
 * Configuration change ve orientation güvenli; ağır iş Default dispatcher'da.
 */
class HicriTakvimViewModel : ViewModel() {

    private val repository = HicriTakvimRepository()

    private val _cardState = MutableStateFlow<HicriCardState>(HicriCardState.Loading)
    val cardState: StateFlow<HicriCardState> = _cardState.asStateFlow()

    private val _cardLiveData = MutableLiveData<HicriCardState>(HicriCardState.Loading)
    val cardLiveData: LiveData<HicriCardState> = _cardLiveData

    private val _yearListState = MutableStateFlow<HicriYearListState>(HicriYearListState.Loading)
    val yearListState: StateFlow<HicriYearListState> = _yearListState.asStateFlow()

    private val _yearListLiveData = MutableLiveData<HicriYearListState>(HicriYearListState.Loading)
    val yearListLiveData: LiveData<HicriYearListState> = _yearListLiveData

    init {
        loadCardData()
    }

    /** Anasayfa kartı için bugünün Hicri/miladi ve yaklaşan gün. */
    fun loadCardData() {
        viewModelScope.launch {
            _cardState.value = HicriCardState.Loading
            _cardLiveData.postValue(HicriCardState.Loading)
            runCatching {
                withContext(Dispatchers.Default) {
                    val todayHijri = repository.getTodayHijriString()
                    val todayGregorian = repository.getTodayGregorianString()
                    val period = repository.getCurrentPeriodIfAny()
                    val next = repository.getNextSpecialDay()
                    Triple(todayHijri, todayGregorian, Pair(period, next))
                }
            }.fold(
                onSuccess = { (todayHijri, todayGregorian, pair) ->
                    val (period, next) = pair
                    val state = HicriCardState.Success(
                        todayHijri = todayHijri,
                        todayGregorian = todayGregorian,
                        periodTitle = period?.first,
                        periodRange = period?.second,
                        nextSpecialDay = next
                    )
                    _cardState.value = state
                    _cardLiveData.postValue(state)
                },
                onFailure = {
                    val state = HicriCardState.Error(it.message ?: "Hesaplama hatası")
                    _cardState.value = state
                    _cardLiveData.postValue(state)
                }
            )
        }
    }

    /** Yıl sayfası için o yılın tüm özel günleri. */
    fun loadYear(year: Int) {
        viewModelScope.launch {
            _yearListState.value = HicriYearListState.Loading
            _yearListLiveData.postValue(HicriYearListState.Loading)
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.getAllSpecialDaysForYear(year)
                }
            }.fold(
                onSuccess = { list ->
                    val state = HicriYearListState.Success(year = year, items = list)
                    _yearListState.value = state
                    _yearListLiveData.postValue(state)
                },
                onFailure = {
                    val state = HicriYearListState.Error(it.message ?: "Liste yüklenemedi")
                    _yearListState.value = state
                    _yearListLiveData.postValue(state)
                }
            )
        }
    }
}
