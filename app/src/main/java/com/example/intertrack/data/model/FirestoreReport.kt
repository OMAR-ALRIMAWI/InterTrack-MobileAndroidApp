package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreReport(
    val reportId: String = "",
    val internshipConnectionId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val internshipId: String = "",
    val internshipTitle: String = "",
    val instructorUid: String = "",
    val supervisorUid: String = "",
    val reportTitle: String = "",
    val reportPeriod: String = "",
    val reportContent: String = "",
    val challenges: String = "",
    val learnedSkills: String = "",
    val hoursWorked: String = "",
    val status: String = "SUBMITTED",
    // Attachment is metadata only — no Firebase Storage. Mirrors the verification document pattern.
    // downloadUrl/storageUrl stay blank unless a real hosted file URL is ever saved; the UI only
    // offers to open the attachment when one of these is a non-blank URL.
    val attachedFileName: String = "",
    val attachedFileMimeType: String = "",
    val downloadUrl: String = "",
    val storageUrl: String = "",
    // ── Weekly period info (0 / null for old reports) ──
    val reportWeekNumber: Int = 0,
    val periodStart: Timestamp? = null,
    val periodEnd: Timestamp? = null,
    val deadlineDate: Timestamp? = null,
    val submittedAt: Timestamp? = null,
    val instructorFeedback: String = "",
    val supervisorFeedback: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null
)
