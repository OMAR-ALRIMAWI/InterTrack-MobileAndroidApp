package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreInstructorRequest(
    val requestId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val studentUniversity: String = "",
    val studentMajor: String = "",
    val studentGpa: String = "",
    val studentCompanyName: String = "",
    val instructorUid: String = "",
    val instructorName: String = "",
    val instructorEmail: String = "",
    val status: String = "PENDING",
    val rejectionReason: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val reviewedByUid: String = ""
)
