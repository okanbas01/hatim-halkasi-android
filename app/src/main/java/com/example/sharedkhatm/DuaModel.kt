package com.example.sharedkhatm

import java.util.Date
import androidx.annotation.Keep
@Keep
data class DuaModel(
    var id: String = "",
    val text: String = "",
    val authorName: String = "",
    val authorId: String = "",
    val aminCount: Int = 0,
    val createdAt: Date = Date(),
    // DÜZELTME: ArrayList yerine List kullanıyoruz ve boş liste veriyoruz
    var likedBy: List<String> = emptyList()
)