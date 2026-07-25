package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.data.model.WeekUtil
import com.example.intertrack.data.model.isValidActive
import com.example.intertrack.data.repository.AddInstructorOutcome
import com.example.intertrack.databinding.FragmentMyInternshipBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class MyInternshipFragment : Fragment(), ApplicationDetailsBottomSheet.OnActionListener {

    private var _binding: FragmentMyInternshipBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var activeConnection: FirestoreInternshipConnection? = null
    private var fallbackApplication: FirestoreInternshipApplication? = null
    // Shows the one-time celebration highlight (avoids repeated popups within the same screen visit).
    private var celebrationShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyInternshipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshMyInternship.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshMyInternship.setOnRefreshListener { loadInternshipStatus() }
        loadInternshipStatus()
    }

    override fun onApplicationActioned() {
        loadInternshipStatus()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "MyInternshipFragment -> Loading")
        binding.progressMyInternshipMain.visibility = View.VISIBLE
        binding.myInternshipContent.visibility = View.GONE
        binding.tvMyInternshipError.visibility = View.GONE

        // Clear visible fields
        binding.tvInternshipCompanyName.text = ""
        binding.tvInternshipPosition.text = ""
        binding.tvApplicationStatus.text = ""
        binding.tvAppStatusCompanyName.text = ""
        binding.tvInternshipInstructor.text = ""
        binding.tvInternshipSupervisor.text = ""
        binding.tvInternshipStart.text = ""
        binding.tvInternshipEnd.text = ""
        binding.tvProgressPct.text = ""
        binding.progressInternship.progress = 0
        binding.tvMyInternReportsSubmitted.text = "0"
        binding.tvMyInternReportsPending.text = "0"
        binding.cardWeeklyPeriod.visibility = View.GONE
        binding.cardCompletion.visibility = View.GONE
        binding.cardEndedByCompany.visibility = View.GONE
        binding.btnFindCompanies.visibility = View.GONE
        binding.btnAddInstructorToInternship.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnMyInternProgressChat.visibility = View.GONE
        binding.btnMyInternMsgCompany.visibility = View.GONE
        binding.btnMyInternMsgInstructor.visibility = View.GONE
        binding.btnMyInternViewReports.visibility = View.GONE
        binding.btnMyInternSubmitReport.visibility = View.GONE
        binding.btnViewApplication.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "MyInternshipFragment -> Content")
        binding.progressMyInternshipMain.visibility = View.GONE
        binding.swipeRefreshMyInternship.isRefreshing = false
        binding.myInternshipContent.visibility = View.VISIBLE
        binding.tvMyInternshipError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "MyInternshipFragment -> Empty")
        // "Empty" state in MyInternship is handled by showNoInternshipState() inside the content
        showContentState()
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "MyInternshipFragment -> Error: $message")
        binding.progressMyInternshipMain.visibility = View.GONE
        binding.swipeRefreshMyInternship.isRefreshing = false
        binding.myInternshipContent.visibility = View.GONE
        binding.tvMyInternshipError.visibility = View.VISIBLE
        binding.tvMyInternshipError.text = message
    }

    private fun loadInternshipStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // On pull-to-refresh keep the current content visible (no clear/flash); the swipe spinner
        // covers progress and fresh data replaces it in place.
        if (!binding.swipeRefreshMyInternship.isRefreshing) showLoadingState()

        authRepo.getStudentActiveConnection(
            studentUid = uid,
            onSuccess = { conn ->
                if (_binding == null) return@getStudentActiveConnection
                if (conn != null) {
                    activeConnection = conn
                    fallbackApplication = null
                    showActiveConnection(conn)
                    loadReportStats(uid)
                } else {
                    loadFromApplications(uid)
                }
            },
            onFailure = { message ->
                if (_binding == null) return@getStudentActiveConnection
                showErrorState(message)
            }
        )
    }

    private fun loadFromApplications(uid: String) {
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                val relevant = apps.firstOrNull { it.status == "ACCEPTED" }
                    ?: apps.firstOrNull { it.status == "PENDING" }
                    ?: apps.firstOrNull()
                if (relevant == null) {
                    showNoInternshipState()
                    showContentState()
                } else {
                    fallbackApplication = relevant
                    activeConnection = null
                    showApplicationState(relevant)
                    showContentState()
                }
            },
            onFailure = { message ->
                if (_binding == null) return@getStudentApplications
                showErrorState(message)
            }
        )
    }

    private fun loadReportStats(uid: String) {
        val conn = activeConnection
        authRepo.getStudentReports(
            studentUid = uid,
            onSuccess = { reports ->
                if (_binding == null) return@getStudentReports
                val relevant = if (conn != null) reports.filter { it.internshipConnectionId == conn.connectionId } else reports
                binding.tvMyInternReportsSubmitted.text = relevant.size.toString()
                val pending = relevant.count { it.status != "REVIEWED" && it.status != "REVISION_REQUESTED" }
                binding.tvMyInternReportsPending.text = pending.toString()
                if (conn != null) renderWeeklyPeriodCard(conn, relevant)
                showContentState()
            },
            onFailure = { message ->
                if (_binding == null) return@getStudentReports
                showErrorState(message)
            }
        )
    }

    private fun renderWeeklyPeriodCard(conn: FirestoreInternshipConnection, reportsForConn: List<FirestoreReport>) {
        if (_binding == null) return
        if (conn.isCompleted()) {
            binding.cardWeeklyPeriod.visibility = View.GONE
            val req = conn.requiredReportsCount
            binding.tvCompletionReports.text =
                "Reports submitted: ${reportsForConn.size}${if (req > 0) " / $req" else ""}"
            return
        }
        if (!conn.isValidActive()) {
            binding.cardWeeklyPeriod.visibility = View.GONE
            return
        }
        binding.cardWeeklyPeriod.visibility = View.VISIBLE

        if (!conn.hasPeriod()) {
            binding.tvMyInternPeriodNotSet.visibility = View.VISIBLE
            binding.tvMyInternPeriod.visibility = View.GONE
            binding.tvMyInternWeek.visibility = View.GONE
            binding.tvMyInternDeadline.visibility = View.GONE
            binding.tvMyInternReportCount.visibility = View.GONE
            binding.btnMyInternSubmitReport.isEnabled = false
            binding.btnMyInternSubmitReport.alpha = 0.5f
            return
        }

        binding.tvMyInternPeriodNotSet.visibility = View.GONE
        binding.tvMyInternPeriod.visibility = View.VISIBLE
        binding.tvMyInternWeek.visibility = View.VISIBLE
        binding.tvMyInternDeadline.visibility = View.VISIBLE
        binding.tvMyInternReportCount.visibility = View.VISIBLE

        val startMs = conn.startDate?.toDate()?.time ?: 0L
        val endMs = conn.endDate?.toDate()?.time ?: 0L
        val required = conn.requiredReportsCount
        val now = System.currentTimeMillis()
        val week = WeekUtil.currentWeek(startMs, now)

        binding.tvMyInternPeriod.text =
            "Period: ${WeekUtil.formatDate(startMs)} – ${WeekUtil.formatDate(endMs)} · Weekly"
        if (week in 1..required) {
            binding.tvMyInternWeek.text = "Current week: $week of $required"
            binding.tvMyInternDeadline.text = "Deadline: ${WeekUtil.formatDate(WeekUtil.periodEnd(startMs, week))}"
        } else {
            binding.tvMyInternWeek.text = "Current week: — of $required"
            binding.tvMyInternDeadline.text = "Deadline: —"
        }
        val remaining = if (required > 0) (required - reportsForConn.size).coerceAtLeast(0) else 0
        binding.tvMyInternReportCount.text =
            "Reports submitted: ${reportsForConn.size} / $required · Remaining: $remaining"

        val alreadyThisWeek = week in 1..required && reportsForConn.any { it.reportWeekNumber == week }
        val canSubmit = when {
            now < startMs -> false
            now > endMs -> false
            reportsForConn.size >= required -> false
            week < 1 || week > required -> false
            alreadyThisWeek -> false
            else -> true
        }
        binding.btnMyInternSubmitReport.isEnabled = canSubmit
        binding.btnMyInternSubmitReport.alpha = if (canSubmit) 1f else 0.5f
    }

    private fun onAddInstructorClicked(applicationId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        binding.btnAddInstructorToInternship.isEnabled = false
        Log.d("AddInstructor", "tapped: studentUid=$uid applicationId=$applicationId")

        authRepo.getStudentInstructorRequests(
            studentUid = uid,
            onSuccess = { requests ->
                if (_binding == null) return@getStudentInstructorRequests
                val accepted = requests.firstOrNull { it.status == "ACCEPTED" }
                if (accepted != null) {
                    attachInstructor(applicationId, accepted.instructorUid, accepted.instructorName)
                } else {
                    authRepo.fetchUserDocument(
                        uid = uid,
                        onSuccess = { user ->
                            if (_binding == null) return@fetchUserDocument
                            val iUid = user.assignedInstructorUid ?: ""
                            val iName = user.assignedInstructorName ?: ""
                            if (iUid.isBlank()) {
                                onNoAcceptedInstructor()
                            } else {
                                attachInstructor(applicationId, iUid, iName)
                            }
                        },
                        onFailure = { msg ->
                            if (_binding == null) return@fetchUserDocument
                            onNoAcceptedInstructor()
                        }
                    )
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@getStudentInstructorRequests
                binding.btnAddInstructorToInternship.isEnabled = true
                Toast.makeText(requireContext(), "Could not look up instructor.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun onNoAcceptedInstructor() {
        if (_binding == null) return
        binding.btnAddInstructorToInternship.isEnabled = true
        Toast.makeText(requireContext(),
            "You need an accepted instructor before adding them to this internship.",
            Toast.LENGTH_LONG).show()
        (requireActivity() as? StudentDashBoard)?.openDetail(AddInstructorFragment())
    }

    private fun attachInstructor(applicationId: String, instructorUid: String, instructorName: String) {
        authRepo.addInstructorToInternship(
            applicationId = applicationId,
            instructorUid = instructorUid,
            instructorName = instructorName,
            onResult = { outcome ->
                if (_binding == null) return@addInstructorToInternship
                binding.btnAddInstructorToInternship.isEnabled = true
                Toast.makeText(requireContext(), "Instructor request sent.", Toast.LENGTH_SHORT).show()
                loadInternshipStatus()
            }
        )
    }

    private fun showActiveConnection(conn: FirestoreInternshipConnection) {
        if (conn.isCompleted()) {
            showCompletedConnection(conn)
            return
        }
        if (conn.isEndedByCompany()) {
            showEndedConnection(conn)
            return
        }
        binding.cardEndedByCompany.visibility = View.GONE
        binding.cardCompletion.visibility = View.GONE
        val isActive = conn.isValidActive()

        binding.tvInternshipCompanyName.text = conn.companyName.ifBlank { "—" }
        binding.tvInternshipSupervisor.text = conn.supervisorName.ifBlank { "—" }
        binding.tvInternshipInstructor.text = conn.instructorName.ifBlank {
            if (isActive) "—" else "Waiting for instructor"
        }
        binding.tvAppStatusCompanyName.text = conn.companyName.ifBlank { "" }
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        binding.tvInternshipStart.text = (conn.startDate ?: conn.startedAt)?.toDate()?.let {
            dateFmt.format(it)
        } ?: "—"
        binding.tvInternshipEnd.text = conn.endDate?.toDate()?.let { dateFmt.format(it) } ?: "—"

        if (isActive) {
            binding.tvApplicationStatus.text = "ACTIVE"
            binding.tvInternshipPosition.text = conn.internshipTitle.ifBlank { "Intern" }
            binding.progressInternship.progress = 75
            binding.tvProgressPct.text = "Active"
        } else {
            binding.tvApplicationStatus.text = "WAITING INSTRUCTOR"
            binding.tvInternshipPosition.text = "Waiting for instructor connection."
            binding.progressInternship.progress = 50
            binding.tvProgressPct.text = "50%"
        }

        val needsInstructor = !isActive && conn.instructorUid.isBlank()
        binding.btnAddInstructorToInternship.visibility = if (needsInstructor) View.VISIBLE else View.GONE
        if (needsInstructor) {
            binding.btnAddInstructorToInternship.setOnClickListener {
                onAddInstructorClicked(conn.applicationId.ifBlank { conn.connectionId })
            }
        }

        binding.btnFindCompanies.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnMyInternProgressChat.visibility = if (isActive) View.VISIBLE else View.GONE
        binding.btnMyInternMsgInstructor.visibility = if (isActive && conn.instructorUid.isNotBlank()) View.VISIBLE else View.GONE
        binding.btnMyInternMsgCompany.visibility = if (isActive && conn.supervisorUid.isNotBlank()) View.VISIBLE else View.GONE
        binding.btnMyInternViewReports.visibility = if (isActive) View.VISIBLE else View.GONE
        binding.btnMyInternSubmitReport.visibility = if (isActive) View.VISIBLE else View.GONE
        binding.btnViewApplication.visibility = if (isActive) View.GONE else View.VISIBLE

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val myName = AppSessionCache.currentUser?.fullName ?: ""

        binding.btnMyInternViewReports.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(
                InternshipReportsFragment.newInstance(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    internshipTitle = conn.internshipTitle,
                    role = "STUDENT"
                )
            )
        }

        binding.btnMyInternSubmitReport.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(CreateReportFragment())
        }

        binding.btnMyInternProgressChat.setOnClickListener {
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
                    (requireActivity() as? StudentDashBoard)?.openDetail(
                        ChatFragment.newInstance(conversationId, "GROUP", "Internship Chat", "")
                    )
                },
                onFailure = { msg ->
                    if (_binding == null) return@getOrCreateProgressConversation
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            )
        }

        binding.btnMyInternMsgCompany.setOnClickListener {
            if (conn.supervisorUid.isBlank()) return@setOnClickListener
            authRepo.getOrCreateConversation(
                uidA = uid, nameA = myName,
                uidB = conn.supervisorUid, nameB = conn.supervisorName,
                onSuccess = { convId ->
                    if (_binding == null) return@getOrCreateConversation
                    (requireActivity() as? StudentDashBoard)?.openDetail(
                        ChatFragment.newInstance(convId, conn.supervisorUid, conn.supervisorName)
                    )
                },
                onFailure = {}
            )
        }

        binding.btnMyInternMsgInstructor.setOnClickListener {
            if (conn.instructorUid.isBlank()) return@setOnClickListener
            openDirectInstructorChat(uid, myName, conn.instructorUid, conn.instructorName.ifBlank { "Instructor" })
        }

        binding.btnViewApplication.setOnClickListener { openDetailsForConnection(conn) }
    }

    private fun showCompletedConnection(conn: FirestoreInternshipConnection) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val myName = AppSessionCache.currentUser?.fullName ?: ""
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        binding.tvInternshipCompanyName.text = conn.companyName.ifBlank { "—" }
        binding.tvInternshipPosition.text = conn.internshipTitle.ifBlank { "Intern" }
        binding.tvAppStatusCompanyName.text = conn.companyName.ifBlank { "" }
        binding.tvInternshipSupervisor.text = conn.supervisorName.ifBlank { "—" }
        binding.tvInternshipInstructor.text = conn.instructorName.ifBlank { "—" }
        binding.tvInternshipStart.text =
            (conn.startDate ?: conn.startedAt)?.toDate()?.let { dateFmt.format(it) } ?: "—"
        binding.tvInternshipEnd.text = conn.endDate?.toDate()?.let { dateFmt.format(it) } ?: "—"
        binding.tvApplicationStatus.text = "COMPLETED"
        binding.progressInternship.progress = 100
        binding.tvProgressPct.text = "Completed"

        binding.cardCompletion.visibility = View.VISIBLE
        binding.tvCompletionMessage.text =
            "You have successfully completed your internship with ${conn.companyName.ifBlank { "your company" }}."
        binding.tvCompletionPeriod.text = if (conn.hasPeriod()) {
            "Period: ${WeekUtil.formatDate(conn.startDate?.toDate()?.time ?: 0L)} – " +
                WeekUtil.formatDate(conn.endDate?.toDate()?.time ?: 0L)
        } else "Period: —"
        binding.tvCompletionDate.text =
            "Completed: ${conn.completedAt?.toDate()?.let { dateFmt.format(it) } ?: "—"}"
        binding.tvCompletionReports.text =
            "Reports submitted: ${conn.submittedReportsCount}${if (conn.requiredReportsCount > 0) " / ${conn.requiredReportsCount}" else ""}"
        binding.tvCompletionCompanyEval.text = conn.companyFinalEvaluationText.ifBlank { "Not available yet." }
        binding.tvCompletionInstructorEval.text = conn.instructorFinalEvaluationText.ifBlank { "Not available yet." }

        binding.cardWeeklyPeriod.visibility = View.GONE
        binding.btnMyInternSubmitReport.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnFindCompanies.visibility = View.GONE
        binding.btnViewApplication.visibility = View.GONE
        binding.btnAddInstructorToInternship.visibility = View.GONE

        binding.btnMyInternViewReports.visibility = View.VISIBLE
        binding.btnMyInternProgressChat.visibility = View.VISIBLE
        binding.btnMyInternMsgInstructor.visibility = if (conn.instructorUid.isNotBlank()) View.VISIBLE else View.GONE
        binding.btnMyInternMsgCompany.visibility = if (conn.supervisorUid.isNotBlank()) View.VISIBLE else View.GONE

        binding.btnMyInternViewReports.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(
                InternshipReportsFragment.newInstance(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    internshipTitle = conn.internshipTitle,
                    role = "STUDENT"
                )
            )
        }
        binding.btnMyInternProgressChat.setOnClickListener {
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
                    (requireActivity() as? StudentDashBoard)?.openDetail(
                        ChatFragment.newInstance(conversationId, "GROUP", "Internship Chat", "")
                    )
                },
                onFailure = { msg ->
                    if (_binding == null) return@getOrCreateProgressConversation
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
        binding.btnMyInternMsgCompany.setOnClickListener {
            if (conn.supervisorUid.isBlank()) return@setOnClickListener
            authRepo.getOrCreateConversation(
                uidA = uid, nameA = myName,
                uidB = conn.supervisorUid, nameB = conn.supervisorName,
                onSuccess = { convId ->
                    if (_binding == null) return@getOrCreateConversation
                    (requireActivity() as? StudentDashBoard)?.openDetail(
                        ChatFragment.newInstance(convId, conn.supervisorUid, conn.supervisorName)
                    )
                },
                onFailure = {}
            )
        }
        binding.btnMyInternMsgInstructor.setOnClickListener {
            if (conn.instructorUid.isBlank()) return@setOnClickListener
            openDirectInstructorChat(uid, myName, conn.instructorUid, conn.instructorName.ifBlank { "Instructor" })
        }

        if (!celebrationShown) {
            celebrationShown = true
            Toast.makeText(requireContext(),
                "Congratulations, your internship is completed!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * The company ended this internship early. Show the red ended card with the reason, keep the
     * details visible for reference, and offer only safe actions (View Reports + messaging). Active
     * actions — Submit Report, Progress Chat, Add Instructor, Find Companies — are hidden.
     */
    private fun showEndedConnection(conn: FirestoreInternshipConnection) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val myName = AppSessionCache.currentUser?.fullName ?: ""
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        binding.tvInternshipCompanyName.text = conn.companyName.ifBlank { "—" }
        binding.tvInternshipPosition.text = conn.internshipTitle.ifBlank { "Intern" }
        binding.tvAppStatusCompanyName.text = conn.companyName.ifBlank { "" }
        binding.tvInternshipSupervisor.text = conn.supervisorName.ifBlank { "—" }
        binding.tvInternshipInstructor.text = conn.instructorName.ifBlank { "—" }
        binding.tvInternshipStart.text =
            (conn.startDate ?: conn.startedAt)?.toDate()?.let { dateFmt.format(it) } ?: "—"
        binding.tvInternshipEnd.text = conn.endDate?.toDate()?.let { dateFmt.format(it) } ?: "—"
        binding.tvApplicationStatus.text = "ENDED"
        binding.tvApplicationStatus.setBackgroundResource(R.drawable.bg_status_needs_mod)
        binding.tvApplicationStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        binding.progressInternship.progress = 0
        binding.tvProgressPct.text = "Ended"

        binding.cardCompletion.visibility = View.GONE
        binding.cardWeeklyPeriod.visibility = View.GONE
        binding.cardEndedByCompany.visibility = View.VISIBLE
        binding.tvEndedReason.text = "Reason: ${conn.endReason.ifBlank { "No reason provided." }}"

        // Hide active actions.
        binding.btnMyInternSubmitReport.visibility = View.GONE
        binding.btnMyInternProgressChat.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnFindCompanies.visibility = View.GONE
        binding.btnAddInstructorToInternship.visibility = View.GONE
        binding.btnViewApplication.visibility = View.GONE

        // Keep safe actions.
        binding.btnMyInternViewReports.visibility = View.VISIBLE
        binding.btnMyInternMsgInstructor.visibility =
            if (conn.instructorUid.isNotBlank()) View.VISIBLE else View.GONE
        binding.btnMyInternMsgCompany.visibility =
            if (conn.supervisorUid.isNotBlank()) View.VISIBLE else View.GONE

        binding.btnMyInternViewReports.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(
                InternshipReportsFragment.newInstance(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    internshipTitle = conn.internshipTitle,
                    role = "STUDENT"
                )
            )
        }
        binding.btnMyInternMsgCompany.setOnClickListener {
            if (conn.supervisorUid.isBlank()) return@setOnClickListener
            authRepo.getOrCreateConversation(
                uidA = uid, nameA = myName,
                uidB = conn.supervisorUid, nameB = conn.supervisorName,
                onSuccess = { convId ->
                    if (_binding == null) return@getOrCreateConversation
                    (requireActivity() as? StudentDashBoard)?.openDetail(
                        ChatFragment.newInstance(convId, conn.supervisorUid, conn.supervisorName)
                    )
                },
                onFailure = {}
            )
        }
        binding.btnMyInternMsgInstructor.setOnClickListener {
            if (conn.instructorUid.isBlank()) return@setOnClickListener
            openDirectInstructorChat(uid, myName, conn.instructorUid, conn.instructorName.ifBlank { "Instructor" })
        }
    }

    private fun openDirectInstructorChat(uid: String, myName: String, instructorUid: String, instructorName: String) {
        authRepo.getOrCreateConversation(
            uidA = uid, nameA = myName,
            uidB = instructorUid, nameB = instructorName,
            onSuccess = { convId ->
                if (_binding == null) return@getOrCreateConversation
                (requireActivity() as? StudentDashBoard)?.openDetail(
                    ChatFragment.newInstance(convId, instructorUid, instructorName)
                )
            },
            onFailure = {}
        )
    }

    private fun openDetailsForConnection(conn: FirestoreInternshipConnection) {
        val sheet = ApplicationDetailsBottomSheet.newInstance(
            role = "STUDENT",
            applicationId = conn.applicationId.ifBlank { conn.connectionId },
            connectionId = conn.connectionId,
            studentUid = conn.studentUid,
            studentName = conn.studentName,
            studentEmail = AppSessionCache.currentUser?.email ?: "",
            university = AppSessionCache.currentUser?.university ?: "",
            major = AppSessionCache.currentUser?.major ?: "",
            academicYear = AppSessionCache.currentUser?.academicYear ?: "",
            skills = "",
            previousExperience = "",
            companyId = conn.companyId,
            companyName = conn.companyName,
            internshipTitle = conn.internshipTitle,
            applicationStatus = if (conn.status == "ACTIVE" || conn.status == "WAITING_INSTRUCTOR_CONNECTION") "ACCEPTED" else conn.status,
            connectionStatus = conn.status,
            rejectionReason = "",
            supervisorUid = conn.supervisorUid,
            supervisorName = conn.supervisorName,
            instructorUid = conn.instructorUid,
            instructorName = conn.instructorName,
            appliedAtMs = 0L,
            reviewedAtMs = conn.startedAt?.toDate()?.time ?: 0L,
            reviewerUid = conn.supervisorUid
        )
        sheet.show(childFragmentManager, "application_details")
    }

    private fun showApplicationState(app: FirestoreInternshipApplication) {
        binding.tvInternshipCompanyName.text = app.companyName.ifBlank { "—" }
        binding.tvAppStatusCompanyName.text = app.companyName.ifBlank { "" }
        binding.tvApplicationStatus.text = app.status
        binding.tvInternshipSupervisor.text = "—"
        binding.tvInternshipInstructor.text = app.assignedInstructorName.ifBlank { "—" }

        var showAddInstructor = false
        when (app.status) {
            "ACCEPTED" -> {
                binding.tvApplicationStatus.text = "WAITING INSTRUCTOR"
                binding.progressInternship.progress = 50
                binding.tvProgressPct.text = "50%"
                binding.tvInternshipPosition.text = "Waiting for instructor connection."
                binding.btnFindCompanies.visibility = View.GONE
                binding.btnViewApplication.visibility = View.VISIBLE
                showAddInstructor = true
            }
            "PENDING" -> {
                binding.progressInternship.progress = 25
                binding.tvProgressPct.text = "25%"
                binding.tvInternshipPosition.text = "Application Under Review"
                binding.btnFindCompanies.visibility = View.GONE
                binding.btnViewApplication.visibility = View.VISIBLE
            }
            "REJECTED" -> {
                binding.progressInternship.progress = 0
                binding.tvProgressPct.text = "0%"
                binding.tvInternshipPosition.text = "Rejected"
                binding.btnFindCompanies.visibility = View.VISIBLE
                binding.btnViewApplication.visibility = View.VISIBLE
            }
            else -> {
                binding.progressInternship.progress = 0
                binding.tvProgressPct.text = "0%"
                binding.tvInternshipPosition.text = "—"
                binding.btnFindCompanies.visibility = View.VISIBLE
            }
        }
        binding.tvInternshipStart.text = "—"
        binding.tvInternshipEnd.text = "—"
        binding.cardWeeklyPeriod.visibility = View.GONE
        binding.cardCompletion.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnMyInternProgressChat.visibility = View.GONE
        binding.btnMyInternMsgCompany.visibility = View.GONE
        binding.btnMyInternMsgInstructor.visibility = View.GONE

        binding.btnAddInstructorToInternship.visibility = if (showAddInstructor) View.VISIBLE else View.GONE
        if (showAddInstructor) {
            binding.btnAddInstructorToInternship.setOnClickListener {
                onAddInstructorClicked(app.applicationId)
            }
        }

        binding.btnFindCompanies.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(StudentInternshipsFragment())
        }
        binding.btnViewApplication.setOnClickListener { openDetailsForApplication(app) }
    }

    private fun openDetailsForApplication(app: FirestoreInternshipApplication) {
        val connStatus = if (app.status == "ACCEPTED") "WAITING_INSTRUCTOR_CONNECTION" else ""
        val sheet = ApplicationDetailsBottomSheet.newInstance(
            role = "STUDENT",
            applicationId = app.applicationId,
            connectionId = app.applicationId,
            studentUid = app.studentUid,
            studentName = app.studentName,
            studentEmail = app.studentEmail,
            university = app.studentUniversity,
            major = app.studentMajor,
            academicYear = app.studentYearLevel,
            skills = app.studentSkills,
            previousExperience = app.previousExperience,
            companyId = app.companyId,
            companyName = app.companyName,
            internshipTitle = app.offerTitle,
            applicationStatus = app.status,
            connectionStatus = connStatus,
            rejectionReason = app.rejectionReason,
            supervisorUid = app.supervisorUid,
            supervisorName = "",
            instructorUid = app.assignedInstructorUid,
            instructorName = app.assignedInstructorName,
            appliedAtMs = app.createdAt?.toDate()?.time ?: 0L,
            reviewedAtMs = app.reviewedAt?.toDate()?.time ?: 0L,
            reviewerUid = app.reviewedByUid
        )
        sheet.show(childFragmentManager, "application_details")
    }

    private fun showNoInternshipState() {
        binding.tvApplicationStatus.text = "No Application"
        binding.tvInternshipCompanyName.text = "—"
        binding.tvAppStatusCompanyName.text = ""
        binding.tvInternshipPosition.text = "—"
        binding.tvInternshipSupervisor.text = "—"
        binding.tvInternshipInstructor.text = "—"
        binding.tvInternshipStart.text = "—"
        binding.tvInternshipEnd.text = "—"
        binding.progressInternship.progress = 0
        binding.tvProgressPct.text = "0%"
        binding.cardWeeklyPeriod.visibility = View.GONE
        binding.cardCompletion.visibility = View.GONE
        binding.btnFindCompanies.visibility = View.VISIBLE
        binding.btnViewApplication.visibility = View.GONE
        binding.btnAddInstructorToInternship.visibility = View.GONE
        binding.btnMyInternHub.visibility = View.GONE
        binding.btnMyInternProgressChat.visibility = View.GONE
        binding.btnMyInternMsgCompany.visibility = View.GONE
        binding.btnMyInternMsgInstructor.visibility = View.GONE
        binding.btnFindCompanies.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.openDetail(StudentInternshipsFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("My Internship")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
