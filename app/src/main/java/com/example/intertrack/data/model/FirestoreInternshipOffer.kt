package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreInternshipOffer(
    val offerId: String = "",
    val supervisorUid: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val supervisorName: String = "",
    val title: String = "",
    val department: String = "",
    val description: String = "",
    val requirements: String = "",
    val duration: String = "",
    val seats: Int = 0,
    val status: String = "OPEN",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
