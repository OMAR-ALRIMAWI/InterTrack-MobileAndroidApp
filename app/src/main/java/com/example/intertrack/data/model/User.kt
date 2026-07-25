package com.example.intertrack.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val role: String = "",
    val accountStatus: String = "",
    val verifiedBy: String? = null,
    val rejectionReason: String? = null,
    // Role-specific profile fields (optional, populated from users/{uid})
    val university: String? = null,
    val universityKey: String? = null,
    val department: String? = null,
    val major: String? = null,
    val academicYear: String? = null,
    val bio: String? = null,
    val office: String? = null,
    val companyId: String? = null,
    val position: String? = null,
    // Relationship fields set after acceptance
    val assignedInstructorUid: String? = null,
    val assignedInstructorName: String? = null
) : Parcelable {

    fun resolvedRole(): UserRole? = UserRole.fromString(role)
    fun resolvedStatus(): AccountStatus? = AccountStatus.fromString(accountStatus)

    fun isActive(): Boolean = accountStatus == AccountStatus.ACTIVE.value
    fun isPending(): Boolean = accountStatus == AccountStatus.PENDING.value
    fun isRejected(): Boolean = accountStatus == AccountStatus.REJECTED.value
    fun isBlocked(): Boolean = accountStatus == AccountStatus.BLOCKED.value
    fun isDeleted(): Boolean = accountStatus == AccountStatus.DELETED.value

    fun initials(): String {
        val parts = fullName.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }
}
