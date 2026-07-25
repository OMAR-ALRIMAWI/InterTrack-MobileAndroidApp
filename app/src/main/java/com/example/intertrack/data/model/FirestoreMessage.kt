package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreMessage(
    val messageId: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val messageText: String = "",
    val createdAt: Timestamp? = null,
    val isRead: Boolean = false
)
