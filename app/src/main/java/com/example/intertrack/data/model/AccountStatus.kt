package com.example.intertrack.data.model

enum class AccountStatus(val value: String) {
    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
    REJECTED("REJECTED"),
    BLOCKED("BLOCKED"),
    DELETED("DELETED");

    companion object {
        fun fromString(value: String?): AccountStatus? =
            entries.find { it.value == value }

        val validValues: Set<String> = entries.map { it.value }.toSet()
    }
}
