package com.example.intertrack.data.model

import com.google.firebase.Timestamp

/**
 * A Student/Instructor request to change their verified university. The user's own university is
 * NEVER updated on creation — only an admin approval applies the change to users/{uid}.
 * Attachment is metadata-only (no Firebase Storage), mirroring the report/verification pattern.
 */
data class FirestoreUniversityChangeRequest(
    val requestId: String = "",
    val userUid: String = "",
    val userRole: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val currentUniversity: String = "",
    val currentUniversityKey: String = "",
    val requestedUniversity: String = "",
    val requestedUniversityKey: String = "",
    val reason: String = "",
    val proofFileName: String = "",
    val proofMimeType: String = "",
    val proofUrl: String? = null,
    val status: String = "PENDING",
    val createdAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val reviewedByAdminUid: String = "",
    val rejectionReason: String = ""
)
