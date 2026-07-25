package com.example.intertrack.data.repository

import com.example.intertrack.data.model.AdminLog
import com.example.intertrack.data.model.AdminStats
import com.example.intertrack.data.model.FirestoreCompany
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.data.model.FirestoreConversation
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.FirestoreDocumentMeta
import com.example.intertrack.data.model.FirestoreMessage
import com.example.intertrack.data.model.FirestoreNotification
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.data.model.PendingUser
import com.example.intertrack.data.model.StudentVerification
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.google.firebase.auth.FirebaseUser

/** Result of a company-side request asking the student's instructor to confirm a connection. */
enum class InstructorConfirmationOutcome {
    ALREADY_ACTIVE,   // connection is already confirmed/active — open the hub
    REQUEST_SENT,     // a confirmation request was (re)sent to the assigned instructor
    NO_INSTRUCTOR,    // the student has no accepted instructor linked yet
    NOT_FOUND,        // no waiting connection to act on
    ERROR             // unexpected failure
}

/** Result of the student "Add Instructor to Internship" action. */
enum class AddInstructorOutcome {
    LINKED,             // instructor attached (connection created or updated) + instructor notified
    ALREADY_ACTIVE,     // internship is already active — nothing to do
    ALREADY_WAITING,    // this instructor is already attached and awaiting confirmation
    NO_APPLICATION,     // no application/connection to attach to
    NO_COMPANY,         // the application has no company/supervisor to link
    PERMISSION_DENIED,  // Firestore rules rejected the connection write (rules not published)
    ERROR               // unexpected failure
}

interface AuthRepository {

    fun login(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    )

    fun register(
        email: String,
        password: String,
        fullName: String,
        role: UserRole,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    )

    fun logout()

    fun currentFirebaseUser(): FirebaseUser?

    fun fetchUserDocument(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Profile updates ───────────────────────────────────────────────────────

    fun updateUserProfile(
        uid: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Student verification ──────────────────────────────────────────────────

    fun saveStudentVerification(
        verification: StudentVerification,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun fetchStudentVerification(
        uid: String,
        onSuccess: (StudentVerification?) -> Unit,
        onFailure: (String) -> Unit
    )

    /** Save a company supervisor's verification to `verifications/{uid}` with role tag. */
    fun saveCompanyVerification(
        verification: com.example.intertrack.data.model.CompanyVerification,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** Fetch the company supervisor verification for `uid`, or null if not found. */
    fun fetchCompanyVerification(
        uid: String,
        onSuccess: (com.example.intertrack.data.model.CompanyVerification?) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Companies directory ───────────────────────────────────────────────────

    fun fetchCompanies(
        onSuccess: (List<FirestoreCompany>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun fetchCompany(
        companyId: String,
        onSuccess: (FirestoreCompany?) -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * Resolves the CURRENT canonical company name for the given supervisor. Prevents stale offer
     * snapshots (like an old "TechCorp Turkey" companyName saved on the offer doc) from leaking
     * into Offer Details / Apply / Application Status screens.
     *
     * Lookup priority: users/{supervisorUid}.companyName → verifications/{supervisorUid}.companyName
     * → companies where supervisorUid == this → [fallbackSnapshot]. Empty string on total miss.
     */
    fun resolveCompanyNameForSupervisor(
        supervisorUid: String,
        fallbackSnapshot: String,
        onResult: (String) -> Unit
    )

    fun saveCompany(
        companyId: String?,
        supervisorUid: String,
        data: Map<String, Any?>,
        onSuccess: (newCompanyId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Instructor directory ──────────────────────────────────────────────────

    fun fetchActiveInstructors(
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Internship offers ─────────────────────────────────────────────────────

    fun publishInternshipOffer(
        offer: FirestoreInternshipOffer,
        onSuccess: (offerId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getCompanyActiveOffers(
        supervisorUid: String,
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getOpenInternshipOffers(
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    )

    /** Reads a single offer by id. Returns null if it does not exist (e.g. hard-deleted). */
    fun getOfferById(
        offerId: String,
        onSuccess: (FirestoreInternshipOffer?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun closeInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** Reopens a CLOSED offer (status back to OPEN). DELETED offers are never reopened. */
    fun reopenInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun updateInternshipOffer(
        offerId: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun deleteInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Internship applications ───────────────────────────────────────────────

    fun submitInternshipApplication(
        application: FirestoreInternshipApplication,
        onSuccess: (applicationId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getApplicationByStudentAndCompany(
        studentUid: String,
        companyId: String,
        onSuccess: (FirestoreInternshipApplication?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getStudentApplications(
        studentUid: String,
        onSuccess: (List<FirestoreInternshipApplication>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getCompanyApplications(
        supervisorUid: String,
        onSuccess: (List<FirestoreInternshipApplication>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun acceptInternshipApplication(
        applicationId: String,
        reviewerUid: String,
        startDateMs: Long,
        endDateMs: Long,
        requiredReportsCount: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun rejectInternshipApplication(
        applicationId: String,
        reviewerUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Instructor requests ───────────────────────────────────────────────────

    fun submitInstructorRequest(
        request: FirestoreInstructorRequest,
        onSuccess: (requestId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getRequestByStudentAndInstructor(
        studentUid: String,
        instructorUid: String,
        onSuccess: (FirestoreInstructorRequest?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getInstructorRequests(
        instructorUid: String,
        onSuccess: (List<FirestoreInstructorRequest>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun acceptInstructorRequest(
        requestId: String,
        studentUid: String,
        instructorUid: String,
        instructorName: String,
        reviewerUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun rejectInstructorRequest(
        requestId: String,
        reviewerUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Admin: pending users ──────────────────────────────────────────────────

    fun fetchPendingUsers(
        onSuccess: (List<PendingUser>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun approveUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun rejectUser(
        targetUid: String,
        adminUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Admin: account management ─────────────────────────────────────────────

    fun fetchAllUsers(
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun blockUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun unblockUser(
        targetUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun deleteUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Offers by company (student company profile) ───────────────────────────

    fun getOffersByCompany(
        companyId: String,
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Student instructor requests ───────────────────────────────────────────

    fun getStudentInstructorRequests(
        studentUid: String,
        onSuccess: (List<FirestoreInstructorRequest>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Notifications ─────────────────────────────────────────────────────────

    fun getNotifications(
        recipientUid: String,
        onSuccess: (List<FirestoreNotification>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun markNotificationRead(
        notificationId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun hasUnreadNotifications(
        recipientUid: String,
        onResult: (Boolean) -> Unit
    )

    // ── Admin: dashboard stats ────────────────────────────────────────────────

    fun fetchAdminStats(
        onSuccess: (AdminStats) -> Unit,
        onFailure: (String) -> Unit
    )

    fun fetchRecentActivity(
        onSuccess: (List<AdminLog>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Reports ────────────────────────────────────────────────────────────────

    fun submitReport(
        report: FirestoreReport,
        onSuccess: (reportId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun updateReport(
        reportId: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun getReportById(
        reportId: String,
        onSuccess: (FirestoreReport?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getStudentReports(
        studentUid: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * One-time/lazy repair for OLD reports created before reports carried routing fields. Fills the
     * blank internshipConnectionId/instructorUid/supervisorUid/companyId/internshipId/internshipTitle
     * on the student's own reports from their ACTIVE connection, so instructor/company queries find
     * them. Idempotent; only touches blanks; only the student (report owner) can do this.
     */
    fun backfillStudentReports(
        studentUid: String,
        onDone: () -> Unit
    )

    fun getInstructorReports(
        instructorUid: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getCompanyReports(
        companyId: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun reviewReport(
        reportId: String,
        studentUid: String,
        status: String,
        feedback: String,
        reviewerUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun addSupervisorFeedback(
        reportId: String,
        feedback: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── University change requests ─────────────────────────────────────────────

    /**
     * Fan-out an ADMIN_ACCOUNT_REQUEST notification to every ACTIVE admin. Called by the Student /
     * Instructor / Company Supervisor verification activities after their proof has been saved so
     * admins never see a notification for an incomplete request.
     */
    fun notifyAdminsOfVerificationSubmitted(
        newUserUid: String,
        fullName: String,
        role: String
    )

    fun createUniversityChangeRequest(
        request: com.example.intertrack.data.model.FirestoreUniversityChangeRequest,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** The user's most recent PENDING university change request, or null if none. */
    fun getPendingUniversityChangeRequest(
        userUid: String,
        onSuccess: (com.example.intertrack.data.model.FirestoreUniversityChangeRequest?) -> Unit,
        onFailure: (String) -> Unit
    )

    /** All university change requests, newest first (admin review list). */
    fun getUniversityChangeRequests(
        onSuccess: (List<com.example.intertrack.data.model.FirestoreUniversityChangeRequest>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun approveUniversityChangeRequest(
        request: com.example.intertrack.data.model.FirestoreUniversityChangeRequest,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun rejectUniversityChangeRequest(
        requestId: String,
        userUid: String,
        adminUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Messaging ──────────────────────────────────────────────────────────────

    fun getOrCreateConversation(
        uidA: String,
        nameA: String,
        uidB: String,
        nameB: String,
        onSuccess: (conversationId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun sendMessage(
        conversationId: String,
        senderUid: String,
        senderName: String,
        receiverUid: String,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun getMessages(
        conversationId: String,
        onSuccess: (List<FirestoreMessage>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getUserConversations(
        uid: String,
        onSuccess: (List<FirestoreConversation>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun markConversationRead(
        conversationId: String,
        uid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * Lazily names an old progress conversation that has no internshipTitle stored, so its list row
     * and chat header read "<title> - Internship Chat". No-op if already named. Any participant may
     * do this (conversation update is participant-gated).
     */
    fun normalizeProgressConversation(
        conversationId: String,
        internshipTitle: String,
        onDone: () -> Unit
    )

    // ── Companies with open offers ────────────────────────────────────────────

    fun fetchCompaniesWithOpenOffers(
        onSuccess: (List<FirestoreCompany>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Open offers with their companies (offer-first Explore) ────────────────

    fun fetchOpenOffersWithCompanies(
        onSuccess: (List<com.example.intertrack.data.model.ExploreOffer>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Password change ────────────────────────────────────────────────────────

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Internship connections ─────────────────────────────────────────────────

    fun getStudentActiveConnection(
        studentUid: String,
        onSuccess: (FirestoreInternshipConnection?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getConnectionById(
        connectionId: String,
        onSuccess: (FirestoreInternshipConnection?) -> Unit,
        onFailure: (String) -> Unit
    )

    fun createInternshipConnection(
        connection: FirestoreInternshipConnection,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun getCompanyConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * All of a company's internship connections across the review-relevant statuses
     * (ACTIVE, COMPLETED, ENDED_BY_COMPANY, legacy ENDED) for the Review Internships list.
     * Does not replace [getCompanyConnections] (ACTIVE-only, used by the Home dashboard).
     */
    fun getCompanyAllConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    /** COMPLETED internships assigned to this instructor (history section in the Review list). */
    fun getInstructorCompletedConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun endInternshipConnection(
        connectionId: String,
        studentUid: String,
        instructorUid: String,
        reason: String,
        companyId: String,
        companyName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    fun cancelApplication(
        applicationId: String,
        studentUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * Instructor removes/unfollows the student from a supervised internship. Flips the connection
     * to `ENDED_BY_INSTRUCTOR` (soft — no doc is deleted), captures who/when/why, and notifies both
     * the student and the company supervisor. After this the student may apply to another offer
     * (the global one-active guard treats ENDED_BY_INSTRUCTOR as "not active").
     */
    fun endInternshipByInstructor(
        connectionId: String,
        studentUid: String,
        instructorUid: String,
        supervisorUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Progress group chat ────────────────────────────────────────────────────

    fun getOrCreateProgressConversation(
        connectionId: String,
        studentUid: String,
        studentName: String,
        supervisorUid: String,
        supervisorName: String,
        instructorUid: String,
        instructorName: String,
        onSuccess: (conversationId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun connectInternship(
        connectionId: String,
        instructorUid: String,
        instructorName: String,
        studentUid: String,
        studentName: String,
        supervisorUid: String,
        supervisorName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** Sets/updates the internship period on an existing connection (COMPANY only, e.g. old internships). */
    fun setInternshipPeriod(
        connectionId: String,
        startDateMs: Long,
        endDateMs: Long,
        requiredReportsCount: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** Company supervisor submits their final internship evaluation. Completes the internship once both evaluations exist. */
    fun submitCompanyFinalEvaluation(
        connectionId: String,
        evaluationText: String,
        rating: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /** Instructor submits their final academic evaluation. Completes the internship once both evaluations exist. */
    fun submitInstructorFinalEvaluation(
        connectionId: String,
        evaluationText: String,
        rating: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * Number of COMPLETED internships for a student, scoped to what the caller may read:
     * STUDENT sees their own; COMPANY sees ones at scopeCompanyId; INSTRUCTOR sees ones they supervise.
     */
    fun getCompletedInternshipCount(
        studentUid: String,
        scopeRole: String,
        scopeUid: String,
        scopeCompanyId: String,
        onResult: (Int) -> Unit
    )

    /** Full list of this student's COMPLETED internships, newest completion first. */
    fun getStudentCompletedConnections(
        studentUid: String,
        onSuccess: (List<com.example.intertrack.data.model.FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getInstructorPendingConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    /** ACTIVE internships assigned to this instructor (for the instructor Internships list). */
    fun getInstructorActiveConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    /** Company-ended internships assigned to this instructor (shown as ended cards in the list). */
    fun getInstructorEndedConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    /**
     * Student-side fix for the ordering gap: when a connection was created before the
     * student's instructor was accepted, its instructorUid is blank and the instructor
     * never sees it. The student (the only party who can read their own instructor AND
     * write their own connection) attaches the instructor to their own WAITING connection
     * and notifies them. Reports back whether the document actually changed.
     */
    fun linkInstructorToWaitingConnection(
        connectionId: String,
        instructorUid: String,
        instructorName: String,
        onResult: (changed: Boolean) -> Unit
    )

    /**
     * Company-side action behind "Request Instructor Confirmation": (re)notifies the
     * student's assigned instructor for a WAITING connection. Does not open the hub.
     */
    fun requestInstructorConfirmation(
        connectionId: String,
        onResult: (InstructorConfirmationOutcome) -> Unit
    )

    /**
     * Student "Add Instructor to Internship": attaches the student's accepted instructor to the
     * internship identified by [applicationId] (connectionId == applicationId). Creates the
     * internshipConnections document from the application when it does not exist yet (old data),
     * or updates the existing WAITING/REJECTED one, then notifies the instructor.
     */
    fun addInstructorToInternship(
        applicationId: String,
        instructorUid: String,
        instructorName: String,
        onResult: (AddInstructorOutcome) -> Unit
    )

    fun repairInvalidActiveConnection(
        connectionId: String,
        onRepaired: (Boolean) -> Unit
    )

    fun getCompanyWaitingConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    )

    // ── Documents (metadata only — no Storage) ────────────────────────────────

    fun recordDocumentSelection(
        document: FirestoreDocumentMeta,
        onSuccess: (documentId: String) -> Unit,
        onFailure: (String) -> Unit
    )

    fun getDocumentsFor(
        relatedToId: String,
        onSuccess: (List<FirestoreDocumentMeta>) -> Unit,
        onFailure: (String) -> Unit
    )
}
