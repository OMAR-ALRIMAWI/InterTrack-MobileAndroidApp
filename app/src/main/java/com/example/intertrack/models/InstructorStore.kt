package com.example.intertrack.models

/**
 * Session-scoped store for instructor supervision state.
 * Holds students accepted via the Requests page so they appear on the Dashboard.
 * TODO: replace with backend-backed repository when API is ready.
 */
object InstructorStore {

    data class SupervisedStudent(
        val initials: String,
        val name: String,
        val department: String,
        val university: String,
        val company: String,
        val progress: Int = 0,
        val avatarColor: String = "#0569BF"
    )

    data class PendingRequest(
        val initials: String,
        val name: String,
        val department: String,
        val university: String,
        val company: String,
        val gpa: String,
        val message: String = ""
    )

    // ── Default dummy students already on the dashboard ──────────────────────
    private val defaultStudents = mutableListOf(
        SupervisedStudent("AY", "Ali Yılmaz",     "Computer Engineering", "Istanbul Technical University", "Arçelik A.Ş.",  progress = 65, avatarColor = "#0569BF"),
        SupervisedStudent("ZA", "Zeynep Arslan",  "Software Engineering", "Bogazici University",            "Türk Telekom",  progress = 48, avatarColor = "#7C3AED"),
        SupervisedStudent("MK", "Mehmet Kaya",    "Industrial Engineering","METU",                          "Trendyol",      progress = 82, avatarColor = "#16A34A")
    )

    // ── Students accepted via InstructorRequestsFragment ─────────────────────
    private val acceptedStudents = mutableListOf<SupervisedStudent>()

    /** Returns all supervised students (default + accepted from requests). */
    fun getAllStudents(): List<SupervisedStudent> = defaultStudents + acceptedStudents

    /** Accepts a supervision request and adds the student to the dashboard. */
    fun acceptRequest(request: PendingRequest) {
        if (getAllStudents().none { it.name == request.name }) {
            acceptedStudents.add(
                SupervisedStudent(
                    initials   = request.initials,
                    name       = request.name,
                    department = request.department,
                    university = request.university,
                    company    = request.company,
                    progress   = 0,
                    avatarColor = "#D97706"
                )
            )
        }
    }

    fun hasAcceptedAny(): Boolean = acceptedStudents.isNotEmpty()

    fun getAcceptedCount(): Int = acceptedStudents.size
}
