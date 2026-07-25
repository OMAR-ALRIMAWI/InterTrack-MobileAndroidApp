package com.example.intertrack.models

/**
 * Session-scoped store for Company Supervisor state.
 * Holds active interns and application decisions for the current session.
 * TODO: Replace with backend-backed repository when API is ready.
 */
object CompanyStore {

    data class CompanyIntern(
        val initials: String,
        val name: String,
        val department: String,
        val university: String,
        val position: String,
        val instructorName: String,
        val progress: Int,
        val avatarColor: String = "#0569BF",
        val status: String = "Active"
    )

    data class InternApplication(
        val initials: String,
        val name: String,
        val major: String,
        val university: String,
        val year: String,
        val position: String,
        val skills: String,
        val appliedDate: String,
        val avatarColor: String = "#0569BF",
        var status: String = "Pending"
    )

    // ── Default active interns ──────────────────────────────────────────────
    private val defaultInterns = mutableListOf(
        CompanyIntern("AY","Ali Yılmaz",     "Computer Engineering", "Istanbul Technical University",
            "Backend Development", "Dr. Smith",    65, "#0569BF", "Active"),
        CompanyIntern("EC","Elif Çelik",     "Product Design",       "Bilkent University",
            "UX Design",           "Dr. Johnson",  28, "#7C3AED", "Active"),
        CompanyIntern("OŞ","Omar Şahin",     "Computer Engineering", "METU",
            "Data Analytics",      "Dr. Williams", 100,"#16A34A", "Completed")
    )

    // ── Pending applications ────────────────────────────────────────────────
    val applications = mutableListOf(
        InternApplication("AJ","Alex Johnson",   "Software Engineering","Istanbul Technical University",
            "3rd Year","Mobile App Development","Python, Java, Android","June 1, 2025","#0569BF"),
        InternApplication("SW","Sarah Williams", "Product Design",      "Bogazici University",
            "2nd Year","UI/UX Design Internship","Figma, Sketch, Proto","May 28, 2025","#7C3AED"),
        InternApplication("MC","Michael Chen",   "Data Science",        "METU",
            "4th Year","Data Analyst Internship","Python, SQL, TensorFlow","May 20, 2025","#E65100","Accepted")
    )

    // ── Students accepted from applications this session ────────────────────
    private val acceptedThisSession = mutableListOf<InternApplication>()

    fun getActiveInterns(): List<CompanyIntern> = defaultInterns

    fun acceptApplication(app: InternApplication) {
        app.status = "Accepted"
        if (acceptedThisSession.none { it.name == app.name }) {
            acceptedThisSession.add(app)
        }
    }

    fun rejectApplication(app: InternApplication) {
        app.status = "Rejected"
    }

    fun getNewlyAcceptedCount(): Int  = acceptedThisSession.size
    fun hasNewlyAccepted(): Boolean   = acceptedThisSession.isNotEmpty()
}
