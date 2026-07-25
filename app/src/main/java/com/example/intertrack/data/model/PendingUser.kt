package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class PendingUser(
    val uid: String,
    val fullName: String,
    val email: String,
    val role: String,
    val registeredAt: Timestamp? = null
) {
    fun roleDisplayName(): String = when (role) {
        "STUDENT" -> "Student"
        "INSTRUCTOR" -> "Academic Supervisor"
        "COMPANY_SUPERVISOR" -> "Company Supervisor"
        else -> role
    }
}
