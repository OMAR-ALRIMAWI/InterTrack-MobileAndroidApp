package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class StudentVerification(
    val uid: String = "",
    // Reused for both STUDENT and INSTRUCTOR verifications: for INSTRUCTOR the fields carry
    // department/staff-id semantics but the storage shape is identical, so admin reuses the same
    // fetch/read path. `role` is what tells the admin UI how to label the fields.
    val role: String = "",
    val personalIdNumber: String = "",
    val universityIdNumber: String = "",
    val university: String = "",
    val major: String = "",
    val academicYear: String = "",
    val personalIdFileName: String? = null,
    val universityIdFileName: String? = null,
    val personalIdSelected: Boolean = false,
    val universityIdSelected: Boolean = false,
    val status: String = "PENDING",
    val submittedAt: Timestamp? = null
)
