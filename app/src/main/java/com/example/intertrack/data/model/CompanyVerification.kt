package com.example.intertrack.data.model

import com.google.firebase.Timestamp

/**
 * Company Supervisor verification submitted at registration. Stored in `verifications/{uid}` — same
 * collection as student/instructor verification, distinguished by `role`. Attachment is metadata-only
 * (no Firebase Storage); the admin card shows "Document record only - file preview unavailable".
 */
data class CompanyVerification(
    val uid: String = "",
    val role: String = "COMPANY_SUPERVISOR",
    val companyName: String = "",
    val companyEmail: String = "",
    val companyRegistrationNumber: String = "",
    val companyAddress: String = "",
    val companyPhone: String = "",
    val companyWebsite: String = "",
    val supervisorFullName: String = "",
    val supervisorPosition: String = "",
    val commercialRegistrationFileName: String? = null,
    val commercialRegistrationMimeType: String = "",
    val authorizationFileName: String? = null,
    val authorizationMimeType: String = "",
    val proofUrl: String? = null,
    val status: String = "PENDING",
    val submittedAt: Timestamp? = null
)
