package com.example.intertrack.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.R
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.isValidActive
import com.example.intertrack.data.repository.InstructorConfirmationOutcome
import com.example.intertrack.databinding.FragmentCompanyApplicationsBinding
import com.google.firebase.auth.FirebaseAuth

class CompanyApplicationsFragment : Fragment(),
    ApplicantDetailSheet.OnActionListener,
    ApplicationDetailsBottomSheet.OnActionListener {

    private var _binding: FragmentCompanyApplicationsBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var allApplications: List<FirestoreInternshipApplication> = emptyList()
    private var activeConnectionIds: Set<String> = emptySet()
    private var connStateByApp: Map<String, String> = emptyMap()
    private var activeFilter = FILTER_ALL

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_PENDING = "PENDING"
        private const val FILTER_ACCEPTED = "ACCEPTED"
        private const val FILTER_REJECTED = "REJECTED"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvApplications.layoutManager = LinearLayoutManager(requireContext())

        binding.swipeRefreshApplications.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshApplications.setOnRefreshListener { loadApplications() }

        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            activeFilter = when (checkedId) {
                R.id.chipFilterPending  -> FILTER_PENDING
                R.id.chipFilterAccepted -> FILTER_ACCEPTED
                R.id.chipFilterRejected -> FILTER_REJECTED
                else -> FILTER_ALL
            }
            applyFilter()
        }

        loadApplications()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "CompanyApplicationsFragment -> Loading")
        binding.progressApplications.visibility = View.VISIBLE
        binding.rvApplications.visibility = View.GONE
        binding.tvApplicationsEmpty.visibility = View.GONE
        binding.tvApplicationsError.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "CompanyApplicationsFragment -> Content")
        binding.progressApplications.visibility = View.GONE
        binding.rvApplications.visibility = View.VISIBLE
        binding.tvApplicationsEmpty.visibility = View.GONE
        binding.tvApplicationsError.visibility = View.GONE
        binding.swipeRefreshApplications.isRefreshing = false
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "CompanyApplicationsFragment -> Empty")
        binding.progressApplications.visibility = View.GONE
        binding.rvApplications.visibility = View.GONE
        binding.tvApplicationsEmpty.visibility = View.VISIBLE
        binding.tvApplicationsError.visibility = View.GONE
        
        binding.tvApplicationsEmpty.text = when (activeFilter) {
            FILTER_PENDING  -> "No pending applications."
            FILTER_ACCEPTED -> "No accepted applications."
            FILTER_REJECTED -> "No rejected applications."
            else -> "No applications yet."
        }
        binding.swipeRefreshApplications.isRefreshing = false
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "CompanyApplicationsFragment -> Error: $message")
        binding.progressApplications.visibility = View.GONE
        binding.rvApplications.visibility = View.GONE
        binding.tvApplicationsEmpty.visibility = View.GONE
        binding.tvApplicationsError.visibility = View.VISIBLE
        binding.tvApplicationsError.text = message
        binding.swipeRefreshApplications.isRefreshing = false
    }

    private fun loadApplications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        showLoadingState()

        authRepo.getCompanyApplications(
            supervisorUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getCompanyApplications
                allApplications = apps
                updateChipCounts(apps)
                applyFilter()
                loadActiveConnectionStates()
            },
            onFailure = { msg ->
                if (_binding == null) return@getCompanyApplications
                showErrorState(msg ?: "Could not load applications.")
            }
        )
    }

    private fun updateChipCounts(apps: List<FirestoreInternshipApplication>) {
        val pending  = apps.count { it.status == "PENDING" }
        val accepted = apps.count { it.status == "ACCEPTED" }
        val rejected = apps.count { it.status == "REJECTED" }
        val total    = apps.size
        binding.chipFilterAll.text      = "All ($total)"
        binding.chipFilterPending.text  = "Pending ($pending)"
        binding.chipFilterAccepted.text = "Accepted ($accepted)"
        binding.chipFilterRejected.text = "Rejected ($rejected)"
    }

    private fun loadActiveConnectionStates() {
        val companyId = AppSessionCache.currentCompany?.companyId
        if (companyId.isNullOrBlank()) return
        authRepo.getCompanyConnections(
            companyId = companyId,
            onSuccess = { active ->
                if (_binding == null) return@getCompanyConnections
                val activeMap = active.filter { it.isValidActive() }
                    .associate { (it.applicationId.ifBlank { it.connectionId }) to "ACTIVE" }
                authRepo.getCompanyWaitingConnections(
                    companyId = companyId,
                    onSuccess = { waiting ->
                        if (_binding == null) return@getCompanyWaitingConnections
                        val waitingMap = waiting.associate {
                            val key = it.applicationId.ifBlank { it.connectionId }
                            key to if (it.instructorUid.isNotBlank()) "WAITING" else "NO_INSTRUCTOR"
                        }
                        connStateByApp = waitingMap + activeMap
                        activeConnectionIds = activeMap.keys
                        applyFilter()
                    },
                    onFailure = {
                        if (_binding == null) return@getCompanyWaitingConnections
                        connStateByApp = activeMap
                        activeConnectionIds = activeMap.keys
                        applyFilter()
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@getCompanyConnections
                connStateByApp = emptyMap()
                activeConnectionIds = emptySet()
                applyFilter()
            }
        )
    }

    private fun applyFilter() {
        val filtered = when (activeFilter) {
            FILTER_PENDING  -> allApplications.filter { it.status == "PENDING" }
            FILTER_ACCEPTED -> allApplications.filter { it.status == "ACCEPTED" }
            FILTER_REJECTED -> allApplications.filter { it.status == "REJECTED" }
            else -> allApplications
        }
        
        if (filtered.isEmpty()) {
            showEmptyState()
        } else {
            showApplications(filtered)
            showContentState()
        }
    }

    private fun showApplications(apps: List<FirestoreInternshipApplication>) {
        if (_binding == null) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val adapter = InternshipApplicationAdapter(
            items = apps,
            onAccept = { app -> showAcceptDialog(app, uid) },
            onReject = { app -> showRejectDialog(app, uid) },
            onItemClick = { app -> showApplicantDetail(app, uid) },
            onRemove = { app -> showRemoveDialog(app, uid) },
            onProgressHub = { app -> onAcceptedPrimaryAction(app) },
            onMessageStudent = { app -> openMessageStudent(app, uid) },
            onProgressChat = { app -> openProgressChatForApp(app, uid) },
            connStateByApp = connStateByApp
        )
        binding.rvApplications.adapter = adapter
    }

    private fun onAcceptedPrimaryAction(app: FirestoreInternshipApplication) {
        if (app.applicationId in activeConnectionIds) {
            (requireActivity() as? CompanyDashBoard)?.openDetail(
                InternshipProgressHubFragment.newInstance(app.applicationId, "COMPANY")
            )
        } else {
            requestInstructorConfirmation(app.applicationId)
        }
    }

    private fun requestInstructorConfirmation(connectionId: String) {
        authRepo.requestInstructorConfirmation(connectionId) { outcome ->
            if (_binding == null) return@requestInstructorConfirmation
            when (outcome) {
                InstructorConfirmationOutcome.ALREADY_ACTIVE ->
                    (requireActivity() as? CompanyDashBoard)?.openDetail(
                        InternshipProgressHubFragment.newInstance(connectionId, "COMPANY"))
                InstructorConfirmationOutcome.REQUEST_SENT ->
                    Toast.makeText(requireContext(),
                        "Instructor connection request sent.", Toast.LENGTH_LONG).show()
                InstructorConfirmationOutcome.NO_INSTRUCTOR ->
                    Toast.makeText(requireContext(),
                        "Student has not added an instructor yet.", Toast.LENGTH_LONG).show()
                InstructorConfirmationOutcome.NOT_FOUND ->
                    Toast.makeText(requireContext(),
                        "This internship is still being set up.", Toast.LENGTH_LONG).show()
                InstructorConfirmationOutcome.ERROR ->
                    Toast.makeText(requireContext(),
                        "Could not send the request. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showApplicantDetail(app: FirestoreInternshipApplication, supervisorUid: String) {
        if (app.status == "ACCEPTED") {
            authRepo.getConnectionById(
                connectionId = app.applicationId,
                onSuccess = { conn ->
                    if (_binding == null) return@getConnectionById
                    openDetailsSheet(app, supervisorUid, conn?.status ?: "WAITING_INSTRUCTOR_CONNECTION",
                        conn?.supervisorName ?: "", conn?.instructorName ?: app.assignedInstructorName)
                },
                onFailure = {
                    if (_binding == null) return@getConnectionById
                    openDetailsSheet(app, supervisorUid, "WAITING_INSTRUCTOR_CONNECTION", "", app.assignedInstructorName)
                }
            )
        } else {
            openDetailsSheet(app, supervisorUid, "", "", app.assignedInstructorName)
        }
    }

    private fun openDetailsSheet(
        app: FirestoreInternshipApplication,
        supervisorUid: String,
        connectionStatus: String,
        supervisorName: String,
        instructorName: String
    ) {
        val sheet = ApplicationDetailsBottomSheet.newInstance(
            role = "COMPANY",
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
            connectionStatus = connectionStatus,
            rejectionReason = app.rejectionReason,
            supervisorUid = supervisorUid,
            supervisorName = supervisorName.ifBlank { AppSessionCache.currentUser?.fullName ?: "" },
            instructorUid = app.assignedInstructorUid,
            instructorName = instructorName,
            appliedAtMs = app.createdAt?.toDate()?.time ?: 0L,
            reviewedAtMs = app.reviewedAt?.toDate()?.time ?: 0L,
            reviewerUid = supervisorUid
        )
        sheet.show(childFragmentManager, "application_details")
    }

    private fun showAcceptDialog(app: FirestoreInternshipApplication, supervisorUid: String) {
        InternshipPeriodDialog.show(requireContext()) { startMs, endMs, requiredReports ->
            authRepo.acceptInternshipApplication(
                applicationId = app.applicationId,
                reviewerUid = supervisorUid,
                startDateMs = startMs,
                endDateMs = endMs,
                requiredReportsCount = requiredReports,
                onSuccess = {
                    if (_binding == null) return@acceptInternshipApplication
                    Toast.makeText(requireContext(),
                        "${app.studentName}'s application accepted.", Toast.LENGTH_SHORT).show()
                    loadApplications()
                },
                onFailure = { _ ->
                    if (_binding == null) return@acceptInternshipApplication
                    Toast.makeText(requireContext(), "Failed to accept. Please try again.", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun showRejectDialog(app: FirestoreInternshipApplication, supervisorUid: String) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for rejection (optional)"
            setPadding(48, 32, 48, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reject Application")
            .setMessage("Reject ${app.studentName}'s application?")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                authRepo.rejectInternshipApplication(
                    applicationId = app.applicationId,
                    reviewerUid = supervisorUid,
                    reason = reason,
                    onSuccess = {
                        if (_binding == null) return@rejectInternshipApplication
                        Toast.makeText(requireContext(),
                            "${app.studentName}'s application rejected.", Toast.LENGTH_SHORT).show()
                        loadApplications()
                    },
                    onFailure = { _ ->
                        if (_binding == null) return@rejectInternshipApplication
                        Toast.makeText(requireContext(), "Failed to reject. Please try again.", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveDialog(app: FirestoreInternshipApplication, supervisorUid: String) {
        val isEnd = app.status == "ACCEPTED"
        val title = if (isEnd) "End Internship" else "Remove Application"
        val msg = if (isEnd)
            "End ${app.studentName}'s active internship? The student and instructor will be notified."
        else
            "Remove ${app.studentName}'s application? It will be marked CANCELLED."

        // Ending an accepted internship requires a reason (shown to the student and instructor).
        val input = if (isEnd) EditText(requireContext()).apply {
            hint = "Reason for ending this internship"
            setPadding(48, 32, 48, 0)
            minLines = 2
        } else null

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .apply { if (input != null) setView(input) }
            .setPositiveButton(title) { _, _ ->
                if (isEnd) {
                    val reason = input?.text?.toString()?.trim() ?: ""
                    if (reason.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a reason.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    authRepo.endInternshipConnection(
                        connectionId = app.applicationId,
                        studentUid = app.studentUid,
                        instructorUid = app.assignedInstructorUid,
                        reason = reason,
                        companyId = app.companyId,
                        companyName = app.companyName,
                        onSuccess = {
                            if (_binding == null) return@endInternshipConnection
                            Toast.makeText(requireContext(), "Internship ended. Student and instructor notified.", Toast.LENGTH_SHORT).show()
                            loadApplications()
                        },
                        onFailure = { _ ->
                            if (_binding == null) return@endInternshipConnection
                            Toast.makeText(requireContext(), "Failed. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    authRepo.cancelApplication(
                        applicationId = app.applicationId,
                        studentUid = app.studentUid,
                        onSuccess = {
                            if (_binding == null) return@cancelApplication
                            Toast.makeText(requireContext(), "Application removed.", Toast.LENGTH_SHORT).show()
                            loadApplications()
                        },
                        onFailure = { _ ->
                            if (_binding == null) return@cancelApplication
                            Toast.makeText(requireContext(), "Failed. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openMessageStudent(app: FirestoreInternshipApplication, supervisorUid: String) {
        val supervisorName = AppSessionCache.currentUser?.fullName
            ?: AppSessionCache.currentCompany?.name ?: ""
        authRepo.getOrCreateConversation(
            uidA = supervisorUid,
            nameA = supervisorName,
            uidB = app.studentUid,
            nameB = app.studentName,
            onSuccess = { convId ->
                if (_binding == null) return@getOrCreateConversation
                val chat = ChatFragment.newInstance(convId, app.studentUid, app.studentName)
                (requireActivity() as? CompanyDashBoard)?.openDetail(chat)
            },
            onFailure = { msg ->
                if (_binding == null) return@getOrCreateConversation
                Toast.makeText(requireContext(), "Could not open chat: $msg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openProgressChatForApp(app: FirestoreInternshipApplication, supervisorUid: String) {
        val supervisorName = AppSessionCache.currentUser?.fullName
            ?: AppSessionCache.currentCompany?.name ?: ""
        authRepo.getOrCreateProgressConversation(
            connectionId = app.applicationId,
            studentUid = app.studentUid,
            studentName = app.studentName,
            supervisorUid = supervisorUid,
            supervisorName = supervisorName,
            instructorUid = app.assignedInstructorUid,
            instructorName = app.assignedInstructorName,
            onSuccess = { convId ->
                if (_binding == null) return@getOrCreateProgressConversation
                val title = "${app.offerTitle.ifBlank { "Internship" }} - Internship Chat"
                val subtitle = "${app.studentName} • ${app.companyName}"
                val chat = ChatFragment.newInstance(convId, "GROUP", title, subtitle)
                (requireActivity() as? CompanyDashBoard)?.openDetail(chat)
            },
            onFailure = { msg ->
                if (_binding == null) return@getOrCreateProgressConversation
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onApplicationActioned() {
        loadApplications()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Applications")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
