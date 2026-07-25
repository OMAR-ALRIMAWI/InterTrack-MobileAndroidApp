package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreNotification(
    val notificationId: String = "",
    val recipientUid: String = "",
    val recipientRole: String = "",
    val senderUid: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val relatedId: String = "",
    val relatedCompanyId: String = "",
    val relatedInternshipId: String = "",
    // For ADMIN_ACCOUNT_REQUEST: the newly-registered user this request is about.
    val relatedUserId: String = "",
    val relatedUserRole: String = "",
    val targetScreen: String = "",
    val isRead: Boolean = false,
    val createdAt: Timestamp? = null
)
