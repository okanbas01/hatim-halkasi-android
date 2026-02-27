package com.example.sharedkhatm
import androidx.annotation.Keep

@Keep
data class FriendModel(
    val uid: String,
    val name: String,
    val username: String
)