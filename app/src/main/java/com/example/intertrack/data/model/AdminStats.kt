package com.example.intertrack.data.model

data class AdminStats(
    val totalAccounts: Int = 0,
    val totalStudents: Int = 0,
    val totalInstructors: Int = 0,
    val totalCompanySupervisors: Int = 0,
    val pendingRequests: Int = 0,
    val blockedAccounts: Int = 0
)
