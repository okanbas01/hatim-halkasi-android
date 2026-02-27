package com.example.sharedkhatm

import com.google.firebase.Timestamp
import androidx.annotation.Keep

@Keep
data class InvitationModel(
    val inviteId: String,
    val hatimId: String,
    val hatimName: String,
    val senderName: String,
    val status: String,
    val date: Timestamp?
)