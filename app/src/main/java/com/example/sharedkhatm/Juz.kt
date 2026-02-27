package com.example.sharedkhatm
import androidx.annotation.Keep

@Keep
data class Juz(
    val number: Int, // 1, 2, ... 30
    var status: Int = 0, // 0: Boş, 1: Alındı, 2: Tamamlandı
    var ownerName: String? = null, // Alan kişinin ismi
    var ownerId: String? = null // Alan kişinin ID'si (Firebase ID)
)