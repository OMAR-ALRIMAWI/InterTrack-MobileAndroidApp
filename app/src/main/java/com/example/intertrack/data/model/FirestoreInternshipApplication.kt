package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreInternshipApplication(
    val applicationId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val studentUniversity: String = "",
    val studentMajor: String = "",
    val studentYearLevel: String = "",
    val studentPhone: String = "",
    val studentGpa: String = "",
    val studentSkills: String = "",
    val motivation: String = "",
    val startDate: String = "",
    val duration: String = "",
    val preferredDepartment: String = "",
    val previousExperience: String = "",
    val portfolioLink: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val supervisorUid: String = "",
    val offerId: String = "",
    val offerTitle: String = "",
    val assignedInstructorUid: String = "",
    val assignedInstructorName: String = "",
    val status: String = "PENDING",
    val rejectionReason: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val reviewedByUid: String = ""
)
