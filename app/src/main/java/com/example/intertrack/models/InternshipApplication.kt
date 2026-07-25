package com.example.intertrack.models

// TODO: Replace with backend/API data model when server is ready
data class InternshipApplication(
    val companyId: Int = 0,
    val companyName: String = "",
    val studentName: String = "",
    val university: String = "",
    val major: String = "",
    val yearLevel: String = "",
    val email: String = "",
    val phone: String = "",
    val gpa: String = "",
    val skills: String = "",
    val motivation: String = "",
    val startDate: String = "",
    val duration: String = "",
    val preferredDepartment: String = "",
    val previousExperience: String = "",
    val portfolioLink: String = "",
    val cvFileName: String = "",
    val verificationFileName: String = "",
    var status: String = "Not Applied" // Not Applied | Pending | Accepted | Rejected
)

// In-memory singleton store — replace with Room / API when backend is ready
object ApplicationStore {
    val applications = mutableListOf<InternshipApplication>()

    fun getForCompany(companyId: Int): InternshipApplication? =
        applications.find { it.companyId == companyId }

    fun upsert(app: InternshipApplication) {
        val idx = applications.indexOfFirst { it.companyId == app.companyId }
        if (idx >= 0) applications[idx] = app else applications.add(app)
    }
}
