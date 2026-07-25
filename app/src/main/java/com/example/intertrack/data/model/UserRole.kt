package com.example.intertrack.data.model

enum class UserRole(val value: String) {
    STUDENT("STUDENT"),
    INSTRUCTOR("INSTRUCTOR"),
    COMPANY_SUPERVISOR("COMPANY_SUPERVISOR"),
    ADMIN("ADMIN");

    /** Human-readable label for UI display. */
    fun displayName(): String = when (this) {
        STUDENT -> "Student"
        INSTRUCTOR -> "Academic Supervisor"
        COMPANY_SUPERVISOR -> "Company Supervisor"
        ADMIN -> "Administrator"
    }

    companion object {
        /** Kept for backward compatibility with Phase 1 code. */
        const val ACCOUNT_STATUS_ACTIVE = "ACTIVE"

        /** Returns null for any unknown or null string — never throws. */
        fun fromString(value: String?): UserRole? =
            entries.find { it.value == value }

        /** All four valid role strings including ADMIN. */
        val validValues: Set<String> = entries.map { it.value }.toSet()

        /**
         * Roles that a normal user may choose during registration.
         * ADMIN is intentionally excluded — admin accounts are created manually.
         */
        val registrationRoles: Set<String> = setOf(
            STUDENT.value, INSTRUCTOR.value, COMPANY_SUPERVISOR.value
        )
    }
}
