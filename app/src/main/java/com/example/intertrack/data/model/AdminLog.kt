package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class AdminLog(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "",
    val accountStatus: String = "",
    val updatedAt: Timestamp? = null
) {
    fun actionLabel(): String = when (accountStatus) {
        AccountStatus.ACTIVE.value    -> "Account approved"
        AccountStatus.REJECTED.value  -> "Account rejected"
        AccountStatus.BLOCKED.value   -> "Account blocked"
        AccountStatus.PENDING.value   -> "New registration"
        AccountStatus.DELETED.value   -> "Account deleted"
        else                          -> "Status changed"
    }

    fun roleDisplayName(): String = when (role) {
        UserRole.STUDENT.value           -> "Student"
        UserRole.INSTRUCTOR.value        -> "Academic Supervisor"
        UserRole.COMPANY_SUPERVISOR.value -> "Company Supervisor"
        else                              -> role
    }
}
