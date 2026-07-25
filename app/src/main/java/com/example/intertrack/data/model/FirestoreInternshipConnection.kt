package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreInternshipConnection(
    val connectionId: String = "",
    val applicationId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val supervisorUid: String = "",
    val supervisorName: String = "",
    val internshipId: String = "",
    val internshipTitle: String = "",
    val instructorUid: String = "",
    val instructorName: String = "",
    val status: String = "WAITING_INSTRUCTOR_CONNECTION",
    val startedAt: Timestamp? = null,
    val connectedAt: Timestamp? = null,
    val connectedByInstructorUid: String = "",
    val updatedAt: Timestamp? = null,
    // ── Internship period (set by the COMPANY SUPERVISOR; backward compatible defaults) ──
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val reportFrequency: String = "WEEKLY",
    val requiredReportsCount: Int = 0,
    val submittedReportsCount: Int = 0,
    // Audit: who set the period. Always COMPANY_SUPERVISOR going forward.
    val periodSetByUid: String = "",
    val periodSetByRole: String = "",
    val periodSetAt: Timestamp? = null,
    // ── Final evaluations + completion (backward compatible; blank/null for older internships) ──
    val companyFinalEvaluationText: String = "",
    val companyFinalRating: Int = 0,
    val companyFinalSubmittedAt: Timestamp? = null,
    val companyFinalSubmittedByUid: String = "",
    val instructorFinalEvaluationText: String = "",
    val instructorFinalRating: Int = 0,
    val instructorFinalSubmittedAt: Timestamp? = null,
    val instructorFinalSubmittedByUid: String = "",
    val completedAt: Timestamp? = null,
    val completedByUid: String = "",
    // ── Company-ended (early termination with a reason; distinct from COMPLETED) ──
    // status becomes "ENDED_BY_COMPANY" (legacy docs may read "ENDED").
    val endedBy: String = "",
    val endReason: String = "",
    val endedByCompanyId: String = "",
    val endedByCompanyName: String = "",
    val endedAt: Timestamp? = null
) {
    /** The company ended this internship early. Recognizes the legacy "ENDED" status too. */
    fun isEndedByCompany(): Boolean = status == "ENDED_BY_COMPANY" || status == "ENDED"

    /** The instructor removed the student, ending the internship early. */
    fun isEndedByInstructor(): Boolean = status == "ENDED_BY_INSTRUCTOR"

    /**
     * Ended EARLY for any reason (company end / instructor removal / legacy). Used everywhere the UI
     * should treat the internship as terminated-with-reason (red card, submit closed, apply again
     * allowed) — as distinct from COMPLETED, which is a successful completion.
     */
    fun isEndedEarly(): Boolean = isEndedByCompany() || isEndedByInstructor()

    /** True only when a real internship period has been set (old ACTIVE docs may lack it). */
    fun hasPeriod(): Boolean = startDate != null && endDate != null && requiredReportsCount > 0

    /** Both final evaluations are present. */
    fun hasCompanyFinalEvaluation(): Boolean = companyFinalEvaluationText.isNotBlank()
    fun hasInstructorFinalEvaluation(): Boolean = instructorFinalEvaluationText.isNotBlank()
    fun hasBothFinalEvaluations(): Boolean = hasCompanyFinalEvaluation() && hasInstructorFinalEvaluation()

    /** The internship has been marked COMPLETED. */
    fun isCompleted(): Boolean = status == "COMPLETED"

    /**
     * Eligible for the completion workflow: an ACTIVE internship whose period is over OR whose
     * required reports have all been submitted. Not yet COMPLETED. Safe when no period is set (false).
     */
    fun isEligibleForCompletion(nowMs: Long): Boolean {
        if (status != "ACTIVE" || !hasPeriod()) return false
        val endMs = endDate?.toDate()?.time ?: return false
        val pastEnd = nowMs > endMs
        val allReportsIn = requiredReportsCount > 0 && submittedReportsCount >= requiredReportsCount
        return pastEnd || allReportsIn
    }
}

/**
 * A connection only counts as a genuinely-active internship when the instructor has
 * confirmed it. Legacy/partial docs marked ACTIVE without these fields must be treated
 * as WAITING (and repaired). See [com.example.intertrack.data.firebase.FirebaseAuthRepository.repairInvalidActiveConnection].
 */
fun FirestoreInternshipConnection.isValidActive(): Boolean =
    status == "ACTIVE" &&
        instructorUid.isNotBlank() &&
        connectedAt != null &&
        connectedByInstructorUid.isNotBlank() &&
        studentUid.isNotBlank() &&
        supervisorUid.isNotBlank()
