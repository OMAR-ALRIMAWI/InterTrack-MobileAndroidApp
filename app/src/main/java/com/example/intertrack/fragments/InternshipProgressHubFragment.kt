package com.example.intertrack.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentInternshipProgressHubBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class InternshipProgressHubFragment : Fragment() {

    companion object {
        private const val ARG_CONNECTION_ID = "connectionId"
        private const val ARG_ROLE = "role"

        fun newInstance(connectionId: String, role: String): InternshipProgressHubFragment {
            return InternshipProgressHubFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONNECTION_ID, connectionId)
                    putString(ARG_ROLE, role)
                }
            }
        }
    }

    private var _binding: FragmentInternshipProgressHubBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var connection: FirestoreInternshipConnection? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInternshipProgressHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val connectionId = arguments?.getString(ARG_CONNECTION_ID) ?: return
        val role = arguments?.getString(ARG_ROLE) ?: "STUDENT"
        loadHub(connectionId, role)
    }

    private fun loadHub(connectionId: String, role: String) {
        binding.progressHub.visibility = View.VISIBLE
        binding.hubContentLayout.visibility = View.GONE

        authRepo.getConnectionById(
            connectionId = connectionId,
            onSuccess = { conn ->
                if (_binding == null) return@getConnectionById
                binding.progressHub.visibility = View.GONE
                if (conn == null) {
                    Toast.makeText(
                        requireContext(),
                        "This internship connection isn't ready yet. Please try again from Applications.",
                        Toast.LENGTH_LONG
                    ).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    return@getConnectionById
                }
                connection = conn
                bindConnection(conn, role)
                hydrateMissingNames(conn)
                loadReportCounts(conn, role)
            },
            onFailure = { msg ->
                if (_binding == null) return@getConnectionById
                binding.progressHub.visibility = View.GONE
                val friendly = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "You don't have access to this internship hub."
                } else {
                    "Could not load the internship hub. Please try again."
                }
                Toast.makeText(requireContext(), friendly, Toast.LENGTH_LONG).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        )
    }

    private fun bindConnection(conn: FirestoreInternshipConnection, role: String) {
        binding.tvHubTitle.text = conn.internshipTitle.ifBlank { "Internship" }
        binding.tvHubCompanyName.text = conn.companyName.ifBlank { "—" }
        binding.tvHubStatus.text = when (conn.status) {
            "ACTIVE" -> "Active"
            "WAITING_INSTRUCTOR_CONNECTION" -> "Waiting for Instructor"
            "ENDED_BY_COMPANY", "ENDED" -> "Ended by Company"
            else -> conn.status
        }
        binding.tvHubStudentName.text = conn.studentName.ifBlank { "—" }
        binding.tvHubSupervisorName.text = conn.supervisorName.ifBlank { "—" }
        binding.tvHubInstructorName.text = conn.instructorName.ifBlank { "Not yet assigned" }
        val hubDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        // Show the internship period when set (start – end); otherwise fall back to activation date.
        binding.tvHubStartDate.text = if (conn.hasPeriod() && conn.startDate != null && conn.endDate != null) {
            "${hubDateFmt.format(conn.startDate.toDate())} – ${hubDateFmt.format(conn.endDate.toDate())}"
        } else {
            conn.startedAt?.toDate()?.let { hubDateFmt.format(it) } ?: "—"
        }

        binding.tvHubStatus.text = when {
            conn.isCompleted() -> "Completed"
            conn.isEligibleForCompletion(System.currentTimeMillis()) -> "Completion Pending"
            else -> binding.tvHubStatus.text
        }

        binding.hubContentLayout.visibility = View.VISIBLE

        wireButtons(conn, role)
        renderCompletionAndEvals(conn, role)
        loadCompletedCount(conn, role)
        renderEndedBanner(conn)
        renderCompletedState(conn, role)
    }

    /**
     * COMPLETED internship: hide all active/danger actions and replace the red "End Internship"
     * button with a green non-destructive "Completed Internship" indicator. Distinct from
     * [renderEndedBanner] (ENDED_BY_COMPANY) — the two statuses never overlap.
     */
    private fun renderCompletedState(conn: FirestoreInternshipConnection, role: String) {
        if (!conn.isCompleted()) {
            binding.btnHubCompletedState.visibility = View.GONE
            return
        }
        // No active or danger actions once completed.
        binding.btnHubEndInternship.visibility = View.GONE
        binding.hubDangerDivider.visibility = View.GONE
        binding.btnHubSetPeriod.visibility = View.GONE
        binding.btnHubProgressChat.visibility = View.GONE
        binding.btnHubSubmitReport.visibility = View.GONE
        binding.btnHubCompanyFinalEval.visibility = View.GONE
        binding.btnHubInstructorFinalEval.visibility = View.GONE
        binding.btnHubConnect.visibility = View.GONE
        // Green completed indicator (non-destructive: no confirmation dialog).
        binding.btnHubCompletedState.visibility = View.VISIBLE
        binding.btnHubCompletedState.setOnClickListener {
            Toast.makeText(requireContext(),
                "This internship has been completed successfully.", Toast.LENGTH_SHORT).show()
        }
        // Turn the completion card green.
        binding.cardHubCompletion.setCardBackgroundColor(android.graphics.Color.parseColor("#F0FDF4"))
        binding.cardHubCompletion.strokeColor = android.graphics.Color.parseColor("#BBF7D0")
        binding.tvHubCompletionTitle.setTextColor(android.graphics.Color.parseColor("#166534"))
        binding.tvHubCompletionTitle.text = "Internship Completed"
    }

    /**
     * When the company ended the internship early, show a red banner with the reason and suppress the
     * danger/active actions (End Internship, Set Period). Safe actions (View Reports, messaging) stay.
     */
    private fun renderEndedBanner(conn: FirestoreInternshipConnection) {
        if (!conn.isEndedByCompany()) {
            binding.cardHubEnded.visibility = View.GONE
            return
        }
        binding.cardHubEnded.visibility = View.VISIBLE
        binding.tvHubEndedMessage.text = "This internship was ended by the company."
        binding.tvHubEndedReason.text =
            "Reason: ${conn.endReason.ifBlank { "No reason provided." }}"

        // No further active/danger actions on an ended internship.
        binding.btnHubEndInternship.visibility = View.GONE
        binding.hubDangerDivider.visibility = View.GONE
        binding.btnHubSetPeriod.visibility = View.GONE
        binding.btnHubProgressChat.visibility = View.GONE
        binding.btnHubSubmitReport.visibility = View.GONE
        binding.btnHubCompanyFinalEval.visibility = View.GONE
        binding.btnHubInstructorFinalEval.visibility = View.GONE
        binding.btnHubConnect.visibility = View.GONE
    }

    /**
     * Completion card + final-evaluation buttons. The company/instructor get a "Submit Final
     * Evaluation" action once the internship is eligible (past end OR all reports in) and until they
     * have submitted it. The card shows both evaluations and the completion status to everyone.
     */
    private fun renderCompletionAndEvals(
        conn: FirestoreInternshipConnection,
        role: String,
        liveReportCount: Int = -1
    ) {
        // A company-ended internship is never part of the completion workflow: without this guard
        // the async live-report count ("all reports in" → eligible) could re-show the final-eval
        // buttons AFTER renderEndedBanner hid them, and the card would claim "Completion Pending".
        if (conn.isEndedByCompany()) {
            binding.btnHubCompanyFinalEval.visibility = View.GONE
            binding.btnHubInstructorFinalEval.visibility = View.GONE
            binding.cardHubCompletion.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        // "All required reports in" uses the live report count when available (the connection's own
        // submittedReportsCount is best-effort and may lag for ACTIVE internships).
        val allReportsIn = conn.requiredReportsCount > 0 && liveReportCount >= conn.requiredReportsCount
        val eligible = conn.isEligibleForCompletion(now) || conn.isCompleted() || allReportsIn
        val r = role.uppercase()

        // Final-evaluation action buttons.
        val showCompanyEvalBtn = r == "COMPANY" && eligible && !conn.hasCompanyFinalEvaluation()
        val showInstructorEvalBtn = r == "INSTRUCTOR" && eligible && !conn.hasInstructorFinalEvaluation()
        binding.btnHubCompanyFinalEval.visibility = if (showCompanyEvalBtn) View.VISIBLE else View.GONE
        binding.btnHubInstructorFinalEval.visibility = if (showInstructorEvalBtn) View.VISIBLE else View.GONE
        binding.btnHubCompanyFinalEval.setOnClickListener { showFinalEvalDialog(conn, isCompany = true) }
        binding.btnHubInstructorFinalEval.setOnClickListener { showFinalEvalDialog(conn, isCompany = false) }

        // Completion card: shown once completion is in play (eligible, an eval exists, or completed).
        val showCard = conn.isCompleted() || conn.hasCompanyFinalEvaluation() ||
            conn.hasInstructorFinalEvaluation() || eligible
        binding.cardHubCompletion.visibility = if (showCard) View.VISIBLE else View.GONE
        if (showCard) {
            binding.tvHubCompletionStatus.text = when {
                conn.isCompleted() -> {
                    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val done = conn.completedAt?.toDate()?.let { fmt.format(it) } ?: "—"
                    "Your internship has been completed successfully. Completed on $done."
                }
                else -> {
                    val co = if (conn.hasCompanyFinalEvaluation()) "Submitted" else "Not submitted"
                    val ins = if (conn.hasInstructorFinalEvaluation()) "Submitted" else "Not submitted"
                    "Completion Pending — awaiting final evaluations.\n" +
                        "• Final Company Evaluation: $co\n" +
                        "• Final Instructor Evaluation: $ins"
                }
            }
            binding.tvHubCompanyEval.text = conn.companyFinalEvaluationText.ifBlank { "Not submitted yet." }
            binding.tvHubInstructorEval.text = conn.instructorFinalEvaluationText.ifBlank { "Not submitted yet." }
        }
    }

    /** Completed-internship count for this student, scoped to what the viewer may read. */
    private fun loadCompletedCount(conn: FirestoreInternshipConnection, role: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val companyId = conn.companyId.ifBlank { AppSessionCache.currentCompany?.companyId ?: "" }
        authRepo.getCompletedInternshipCount(
            studentUid = conn.studentUid,
            scopeRole = role,
            scopeUid = uid,
            scopeCompanyId = companyId,
            onResult = { count ->
                if (_binding == null) return@getCompletedInternshipCount
                binding.tvHubCompletedCount.visibility = View.VISIBLE
                binding.tvHubCompletedCount.text = "Completed internships: $count"
            }
        )
    }

    private fun showFinalEvalDialog(conn: FirestoreInternshipConnection, isCompany: Boolean) {
        val input = EditText(requireContext()).apply {
            hint = if (isCompany) "Final company evaluation / feedback"
                   else "Final academic evaluation / feedback"
            setPadding(48, 32, 48, 0)
            minLines = 3
        }
        val title = if (isCompany) "Final Company Evaluation" else "Final Instructor Evaluation"
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("This finalizes your evaluation. The internship is marked completed once both the company and instructor have submitted.")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val text = input.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter your evaluation.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                submitFinalEvaluation(conn, isCompany, text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitFinalEvaluation(conn: FirestoreInternshipConnection, isCompany: Boolean, text: String) {
        val onOk = {
            if (_binding != null) {
                Toast.makeText(requireContext(), "Final evaluation submitted.", Toast.LENGTH_SHORT).show()
                loadHub(conn.connectionId, arguments?.getString(ARG_ROLE) ?: "STUDENT")
            }
        }
        val onErr: (String) -> Unit = { msg ->
            if (_binding != null) Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
        }
        if (isCompany) {
            authRepo.submitCompanyFinalEvaluation(conn.connectionId, text, 0, onOk, onErr)
        } else {
            authRepo.submitInstructorFinalEvaluation(conn.connectionId, text, 0, onOk, onErr)
        }
    }

    /**
     * Report counts must use a query the CURRENT role is allowed to make: a company/instructor can't
     * query reports by studentUid (rules match on companyId/instructorUid), which is why the count
     * showed 0. Each role loads via its own allowed query, then filters to this internship.
     */
    private fun loadReportCounts(conn: FirestoreInternshipConnection, role: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val onCounts: (List<com.example.intertrack.data.model.FirestoreReport>) -> Unit = { all ->
            if (_binding != null) {
                val scoped = all.filter {
                    (conn.connectionId.isNotBlank() && it.internshipConnectionId == conn.connectionId) ||
                        it.studentUid == conn.studentUid
                }
                binding.tvHubReportsSubmitted.text = scoped.size.toString()
                binding.tvHubReportsReviewed.text = scoped.count { it.status == "REVIEWED" }.toString()
                // Remaining = expected (period's required count) − submitted, floored at 0. When no
                // period is set the expected count is unknown, so show a dash instead of a number.
                binding.tvHubReportsRemaining.text = if (conn.requiredReportsCount > 0) {
                    (conn.requiredReportsCount - scoped.size).coerceAtLeast(0).toString()
                } else "—"
                // Refine completion eligibility with the live report count (all-required-reports path).
                renderCompletionAndEvals(conn, role, scoped.size)
            }
        }
        when (role.uppercase()) {
            "INSTRUCTOR" -> authRepo.getInstructorReports(uid, onCounts) {}
            "COMPANY" -> {
                val companyId = conn.companyId.ifBlank { AppSessionCache.currentCompany?.companyId ?: "" }
                authRepo.getCompanyReports(companyId, onCounts) {}
            }
            else -> authRepo.getStudentReports(conn.studentUid, onCounts) {}
        }
    }

    /**
     * Old ACTIVE connections may have blank display names. Hydrate the instructor/supervisor names
     * from their user profiles (both are readable by an active user under the rules). Student name is
     * taken from the stored connection (companies can't read student profiles).
     */
    private fun hydrateMissingNames(conn: FirestoreInternshipConnection) {
        if (conn.instructorName.isBlank() && conn.instructorUid.isNotBlank()) {
            authRepo.fetchUserDocument(conn.instructorUid, onSuccess = { user ->
                if (_binding != null && user.fullName.isNotBlank())
                    binding.tvHubInstructorName.text = user.fullName
            }, onFailure = {})
        }
        if (conn.supervisorName.isBlank() && conn.supervisorUid.isNotBlank()) {
            authRepo.fetchUserDocument(conn.supervisorUid, onSuccess = { user ->
                if (_binding != null && user.fullName.isNotBlank())
                    binding.tvHubSupervisorName.text = user.fullName
            }, onFailure = {})
        }
    }

    private fun wireButtons(conn: FirestoreInternshipConnection, role: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val isActive = conn.status == "ACTIVE"

        when (role.uppercase()) {
            "STUDENT" -> {
                if (isActive) binding.btnHubProgressChat.visibility = View.VISIBLE
                if (conn.supervisorUid.isNotBlank()) binding.btnHubMessageSupervisor.visibility = View.VISIBLE
                if (conn.instructorUid.isNotBlank()) binding.btnHubMessageInstructor.visibility = View.VISIBLE
                binding.btnHubViewReports.visibility = View.VISIBLE
                if (isActive) binding.btnHubSubmitReport.visibility = View.VISIBLE
            }
            "COMPANY" -> {
                if (isActive) binding.btnHubProgressChat.visibility = View.VISIBLE
                if (conn.studentUid.isNotBlank()) binding.btnHubMessageStudent.visibility = View.VISIBLE
                if (conn.instructorUid.isNotBlank()) binding.btnHubMessageInstructor.visibility = View.VISIBLE
                binding.btnHubViewReports.visibility = View.VISIBLE
                binding.btnHubEndInternship.visibility = View.VISIBLE
                binding.hubDangerDivider.visibility = View.VISIBLE
            }
            "INSTRUCTOR" -> {
                if (isActive) binding.btnHubProgressChat.visibility = View.VISIBLE
                if (conn.studentUid.isNotBlank()) binding.btnHubMessageStudent.visibility = View.VISIBLE
                if (conn.supervisorUid.isNotBlank()) binding.btnHubMessageSupervisor.visibility = View.VISIBLE
                // Report history stays viewable after completion or company-end (not while waiting).
                if (isActive || conn.isCompleted() || conn.isEndedByCompany())
                    binding.btnHubViewReports.visibility = View.VISIBLE
                // Waiting connection → the instructor confirms it from inside the Hub.
                if (conn.status == "WAITING_INSTRUCTOR_CONNECTION") {
                    binding.tvHubWaitingNote.visibility = View.VISIBLE
                    binding.btnHubConnect.visibility = View.VISIBLE
                }
            }
        }

        // Period is COMPANY-owned. The supervisor can set a missing period (old internships) or edit
        // it — but ONLY before any report is submitted. Instructors/students never set the period.
        val canManagePeriod = conn.status == "ACTIVE" && role.uppercase() == "COMPANY"
        binding.btnHubSetPeriod.visibility = if (canManagePeriod) View.VISIBLE else View.GONE
        if (canManagePeriod) {
            binding.btnHubSetPeriod.text =
                if (conn.hasPeriod()) "Edit Internship Period" else "Set Internship Period"
            binding.btnHubSetPeriod.setOnClickListener { onSetPeriodClicked(conn) }
        }

        binding.btnHubConnect.setOnClickListener { showHubConnectDialog(conn) }

        binding.btnHubProgressChat.setOnClickListener { openProgressChat(conn) }

        binding.btnHubMessageStudent.setOnClickListener {
            openDirectChat(uid, AppSessionCache.currentUser?.fullName ?: "User",
                conn.studentUid, conn.studentName)
        }
        binding.btnHubMessageSupervisor.setOnClickListener {
            openDirectChat(uid, AppSessionCache.currentUser?.fullName ?: "User",
                conn.supervisorUid, conn.supervisorName)
        }
        binding.btnHubMessageInstructor.setOnClickListener {
            openDirectChat(uid, AppSessionCache.currentUser?.fullName ?: "User",
                conn.instructorUid, conn.instructorName)
        }

        binding.btnHubViewReports.setOnClickListener {
            navigateToDetail(
                InternshipReportsFragment.newInstance(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    internshipTitle = conn.internshipTitle,
                    role = role
                )
            )
        }

        binding.btnHubSubmitReport.setOnClickListener {
            Toast.makeText(requireContext(), "Go to Reports tab to submit a report.", Toast.LENGTH_SHORT).show()
        }

        binding.btnHubEndInternship.setOnClickListener {
            confirmEndInternship(conn)
        }
    }

    private fun showHubConnectDialog(conn: FirestoreInternshipConnection) {
        // Instructor confirms the relationship only — the period is company-owned and left as-is.
        val periodNote = if (conn.hasPeriod()) {
            "The company has set the internship period. "
        } else {
            "Note: the company has not set the internship period yet. "
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Connect Internship")
            .setMessage("${periodNote}Connect ${conn.studentName} with ${conn.companyName.ifBlank { "the company" }} now?")
            .setPositiveButton("Connect") { _, _ ->
                val instructorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val instructorName = AppSessionCache.currentUser?.fullName ?: ""
                binding.btnHubConnect.isEnabled = false
                authRepo.connectInternship(
                    connectionId = conn.connectionId,
                    instructorUid = instructorUid,
                    instructorName = instructorName,
                    studentUid = conn.studentUid,
                    studentName = conn.studentName,
                    supervisorUid = conn.supervisorUid,
                    supervisorName = conn.supervisorName,
                    onSuccess = {
                        if (_binding == null) return@connectInternship
                        Toast.makeText(requireContext(),
                            "${conn.studentName}'s internship is now active.",
                            Toast.LENGTH_SHORT).show()
                        loadHub(conn.connectionId, "INSTRUCTOR")
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@connectInternship
                        binding.btnHubConnect.isEnabled = true
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * The period may only be set/edited while NO report has been submitted. Check the live report
     * count first, then open the date dialog; otherwise show the lock message.
     */
    private fun onSetPeriodClicked(conn: FirestoreInternshipConnection) {
        val companyId = conn.companyId.ifBlank { AppSessionCache.currentCompany?.companyId ?: "" }
        authRepo.getCompanyReports(companyId, { all ->
            if (_binding == null) return@getCompanyReports
            val submitted = all.count { it.internshipConnectionId == conn.connectionId }
            if (submitted > 0) {
                Toast.makeText(requireContext(),
                    "Internship period cannot be changed after reports have been submitted.",
                    Toast.LENGTH_LONG).show()
            } else {
                showSetPeriodDialog(conn)
            }
        }, {
            // If we cannot verify reports, be safe and still allow only when no period yet exists.
            if (_binding == null) return@getCompanyReports
            if (conn.hasPeriod()) {
                Toast.makeText(requireContext(),
                    "Could not verify submitted reports. Please try again.", Toast.LENGTH_LONG).show()
            } else showSetPeriodDialog(conn)
        })
    }

    private fun showSetPeriodDialog(conn: FirestoreInternshipConnection) {
        InternshipPeriodDialog.show(
            requireContext(),
            initialStartMs = conn.startDate?.toDate()?.time ?: 0L,
            initialEndMs = conn.endDate?.toDate()?.time ?: 0L
        ) { startMs, endMs, requiredReports ->
            binding.btnHubSetPeriod.isEnabled = false
            authRepo.setInternshipPeriod(
                connectionId = conn.connectionId,
                startDateMs = startMs,
                endDateMs = endMs,
                requiredReportsCount = requiredReports,
                onSuccess = {
                    if (_binding == null) return@setInternshipPeriod
                    Toast.makeText(requireContext(), "Internship period set.", Toast.LENGTH_SHORT).show()
                    loadHub(conn.connectionId, arguments?.getString(ARG_ROLE) ?: "STUDENT")
                },
                onFailure = { msg ->
                    if (_binding == null) return@setInternshipPeriod
                    binding.btnHubSetPeriod.isEnabled = true
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun openProgressChat(conn: FirestoreInternshipConnection) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val myName = AppSessionCache.currentUser?.fullName ?: AppSessionCache.currentCompany?.name ?: ""
        authRepo.getOrCreateProgressConversation(
            connectionId = conn.connectionId,
            studentUid = conn.studentUid,
            studentName = conn.studentName,
            supervisorUid = conn.supervisorUid,
            supervisorName = conn.supervisorName,
            instructorUid = conn.instructorUid,
            instructorName = conn.instructorName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateProgressConversation
                val title = "${conn.internshipTitle.ifBlank { "Internship" }} - Internship Chat"
                val subtitle = "${conn.studentName} • ${conn.companyName}"
                val chat = ChatFragment.newInstance(conversationId, "GROUP", title, subtitle)
                navigateToDetail(chat)
            },
            onFailure = { msg ->
                if (_binding == null) return@getOrCreateProgressConversation
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openDirectChat(myUid: String, myName: String, otherUid: String, otherName: String) {
        if (otherUid.isBlank()) return
        authRepo.getOrCreateConversation(
            uidA = myUid, nameA = myName,
            uidB = otherUid, nameB = otherName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateConversation
                val chat = ChatFragment.newInstance(conversationId, otherUid, otherName)
                navigateToDetail(chat)
            },
            onFailure = { msg ->
                if (_binding == null) return@getOrCreateConversation
                Toast.makeText(requireContext(), "Could not open chat: $msg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmEndInternship(conn: FirestoreInternshipConnection) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for ending this internship"
            setPadding(48, 32, 48, 0)
            minLines = 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("End Internship")
            .setMessage("End ${conn.studentName}'s internship? The student and instructor will be notified. This cannot be undone.")
            .setView(input)
            .setPositiveButton("End Internship") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                if (reason.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a reason.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val companyId = conn.companyId.ifBlank { AppSessionCache.currentCompany?.companyId ?: "" }
                val companyName = conn.companyName.ifBlank { AppSessionCache.currentCompany?.name ?: "" }
                authRepo.endInternshipConnection(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    instructorUid = conn.instructorUid,
                    reason = reason,
                    companyId = companyId,
                    companyName = companyName,
                    onSuccess = {
                        if (_binding == null) return@endInternshipConnection
                        Toast.makeText(requireContext(), "Internship ended. Student and instructor notified.", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@endInternshipConnection
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToDetail(fragment: Fragment) {
        val activity = requireActivity()
        when (activity) {
            is StudentDashBoard -> activity.openDetail(fragment)
            is CompanyDashBoard -> activity.openDetail(fragment)
            is InstructorDashBoard -> activity.openDetail(fragment)
            else -> parentFragmentManager.beginTransaction()
                .replace(id, fragment).addToBackStack(null).commit()
        }
    }

    override fun onResume() {
        super.onResume()
        val conn = connection
        when (val a = requireActivity()) {
            is StudentDashBoard -> a.apply {
                setHeaderVisible(true); setNavVisible(true)
                updateHeader("Internship Hub")
            }
            is CompanyDashBoard -> a.apply {
                setHeaderVisible(true); setNavVisible(true)
                updateHeader("Internship Hub")
            }
            is InstructorDashBoard -> a.apply {
                setHeaderVisible(true); setNavVisible(true)
                updateHeader("Internship Hub")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
