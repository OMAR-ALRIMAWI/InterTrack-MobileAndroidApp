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
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentApplicationDetailsSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real, status-aware application/connection details sheet for BOTH student and company roles.
 * Replaces the old tiny AlertDialog "View Application".
 */
class ApplicationDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentApplicationDetailsSheetBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    interface OnActionListener {
        fun onApplicationActioned()
    }

    companion object {
        private const val A_ROLE = "role"
        private const val A_APP_ID = "appId"
        private const val A_CONN_ID = "connId"
        private const val A_STUDENT_UID = "studentUid"
        private const val A_STUDENT_NAME = "studentName"
        private const val A_STUDENT_EMAIL = "studentEmail"
        private const val A_UNIVERSITY = "university"
        private const val A_MAJOR = "major"
        private const val A_YEAR = "year"
        private const val A_SKILLS = "skills"
        private const val A_EXPERIENCE = "experience"
        private const val A_COMPANY_ID = "companyId"
        private const val A_COMPANY_NAME = "companyName"
        private const val A_TITLE = "internshipTitle"
        private const val A_APP_STATUS = "appStatus"
        private const val A_CONN_STATUS = "connStatus"
        private const val A_REJECTION = "rejection"
        private const val A_SUP_UID = "supUid"
        private const val A_SUP_NAME = "supName"
        private const val A_INS_UID = "insUid"
        private const val A_INS_NAME = "insName"
        private const val A_APPLIED_MS = "appliedMs"
        private const val A_REVIEWED_MS = "reviewedMs"
        private const val A_REVIEWER_UID = "reviewerUid"

        @Suppress("LongParameterList")
        fun newInstance(
            role: String,
            applicationId: String,
            connectionId: String,
            studentUid: String,
            studentName: String,
            studentEmail: String,
            university: String,
            major: String,
            academicYear: String,
            skills: String,
            previousExperience: String,
            companyId: String,
            companyName: String,
            internshipTitle: String,
            applicationStatus: String,
            connectionStatus: String,
            rejectionReason: String,
            supervisorUid: String,
            supervisorName: String,
            instructorUid: String,
            instructorName: String,
            appliedAtMs: Long,
            reviewedAtMs: Long,
            reviewerUid: String
        ): ApplicationDetailsBottomSheet = ApplicationDetailsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(A_ROLE, role)
                putString(A_APP_ID, applicationId)
                putString(A_CONN_ID, connectionId)
                putString(A_STUDENT_UID, studentUid)
                putString(A_STUDENT_NAME, studentName)
                putString(A_STUDENT_EMAIL, studentEmail)
                putString(A_UNIVERSITY, university)
                putString(A_MAJOR, major)
                putString(A_YEAR, academicYear)
                putString(A_SKILLS, skills)
                putString(A_EXPERIENCE, previousExperience)
                putString(A_COMPANY_ID, companyId)
                putString(A_COMPANY_NAME, companyName)
                putString(A_TITLE, internshipTitle)
                putString(A_APP_STATUS, applicationStatus)
                putString(A_CONN_STATUS, connectionStatus)
                putString(A_REJECTION, rejectionReason)
                putString(A_SUP_UID, supervisorUid)
                putString(A_SUP_NAME, supervisorName)
                putString(A_INS_UID, instructorUid)
                putString(A_INS_NAME, instructorName)
                putLong(A_APPLIED_MS, appliedAtMs)
                putLong(A_REVIEWED_MS, reviewedAtMs)
                putString(A_REVIEWER_UID, reviewerUid)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicationDetailsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = requireArguments()

        val role = a.getString(A_ROLE, "STUDENT").uppercase()
        val studentName = a.getString(A_STUDENT_NAME, "")
        val title = a.getString(A_TITLE, "")
        val appStatus = a.getString(A_APP_STATUS, "PENDING")
        val connStatus = a.getString(A_CONN_STATUS, "")
        val companyName = a.getString(A_COMPANY_NAME, "")

        binding.tvAppDetInitials.text = initialsOf(studentName)
        binding.tvAppDetName.text = studentName.ifBlank { "Student" }
        binding.tvAppDetOfferTitle.text = title.ifBlank { "Internship" }

        bindRow(binding.rowAppDetEmail, "Email", a.getString(A_STUDENT_EMAIL, ""))
        bindRow(binding.rowAppDetUniversity, "University", a.getString(A_UNIVERSITY, ""))
        bindRow(binding.rowAppDetMajor, "Major", a.getString(A_MAJOR, ""))
        bindRow(binding.rowAppDetYear, "Year", a.getString(A_YEAR, ""))
        bindRow(binding.rowAppDetSkills, "Skills", a.getString(A_SKILLS, ""))
        bindRow(binding.rowAppDetExperience, "Experience", a.getString(A_EXPERIENCE, ""))

        bindRow(binding.rowAppDetCompany, "Company", companyName)
        bindRow(binding.rowAppDetSupervisor, "Supervisor", a.getString(A_SUP_NAME, ""))
        bindRow(binding.rowAppDetInstructor, "Instructor", a.getString(A_INS_NAME, ""))
        bindRow(binding.rowAppDetApplied, "Applied", formatDate(a.getLong(A_APPLIED_MS, 0L)))
        bindRow(binding.rowAppDetReviewed, "Reviewed", formatDate(a.getLong(A_REVIEWED_MS, 0L)))

        applyStatusBadge(if (connStatus.isNotBlank() && appStatus == "ACCEPTED") connStatus else appStatus)
        applyBanner(appStatus, connStatus, companyName)

        val rejection = a.getString(A_REJECTION, "")
        if (appStatus == "REJECTED" && rejection.isNotBlank()) {
            binding.tvAppDetRejection.text = "Reason: $rejection"
            binding.cardAppDetRejection.visibility = View.VISIBLE
        }

        wireActions(role, appStatus, connStatus)
    }

    private fun applyBanner(appStatus: String, connStatus: String, companyName: String) {
        val co = companyName.ifBlank { "the company" }
        val msg = when {
            appStatus == "ACCEPTED" && connStatus == "WAITING_INSTRUCTOR_CONNECTION" ->
                "Your company ($co) accepted your application. Waiting for your instructor to connect you with the company."
            connStatus == "ACTIVE" ->
                "Your internship is active. You can now use progress chat and reports."
            appStatus == "PENDING" ->
                "Your application is under review by $co."
            appStatus == "REJECTED" -> null
            else -> null
        }
        if (msg != null) {
            binding.tvAppDetBanner.text = msg
            binding.cardAppDetBanner.visibility = View.VISIBLE
        }
    }

    private fun wireActions(role: String, appStatus: String, connStatus: String) {
        val a = requireArguments()
        val isActive = connStatus == "ACTIVE"
        val isWaiting = connStatus == "WAITING_INSTRUCTOR_CONNECTION"
        val connId = a.getString(A_CONN_ID, "").ifBlank { a.getString(A_APP_ID, "") }
        val companyId = a.getString(A_COMPANY_ID, "")
        val supervisorUid = a.getString(A_SUP_UID, "")
        val studentUid = a.getString(A_STUDENT_UID, "")
        val studentName = a.getString(A_STUDENT_NAME, "")
        val supervisorName = a.getString(A_SUP_NAME, "")

        if (role == "COMPANY") {
            if (appStatus == "PENDING") {
                binding.btnAppDetPrimary.text = "Accept Application"
                binding.btnAppDetPrimary.visibility = View.VISIBLE
                binding.btnAppDetPrimary.setOnClickListener { confirmAccept(a.getString(A_APP_ID, ""), studentName, a.getString(A_REVIEWER_UID, "")) }
                binding.btnAppDetSecondary.text = "Reject Application"
                binding.btnAppDetSecondary.visibility = View.VISIBLE
                binding.btnAppDetSecondary.setOnClickListener { confirmReject(a.getString(A_APP_ID, ""), studentName, a.getString(A_REVIEWER_UID, "")) }
            }
            if (isActive) {
                binding.btnAppDetSecondary.text = "End Internship"
                binding.btnAppDetSecondary.visibility = View.VISIBLE
                binding.btnAppDetSecondary.setOnClickListener {
                    confirmEnd(
                        connId, studentUid, studentName,
                        instructorUid = a.getString(A_INS_UID, ""),
                        companyId = companyId,
                        companyName = a.getString(A_COMPANY_NAME, "")
                    )
                }
            }
            if (isWaiting || isActive) {
                binding.btnAppDetHub.text = if (isWaiting) "View Waiting Connection" else "Open Internship Hub"
                binding.btnAppDetHub.visibility = View.VISIBLE
                binding.btnAppDetHub.setOnClickListener {
                    navigate(InternshipProgressHubFragment.newInstance(connId, "COMPANY"))
                }
            }
            if (isActive) showProgressChat(connId, "COMPANY")
            if (studentUid.isNotBlank()) {
                binding.btnAppDetMessage.text = "Message Student"
                binding.btnAppDetMessage.visibility = View.VISIBLE
                binding.btnAppDetMessage.setOnClickListener { openDirectChat(studentUid, studentName) }
            }
        } else {
            // STUDENT
            if (companyId.isNotBlank()) {
                binding.btnAppDetViewCompany.visibility = View.VISIBLE
                binding.btnAppDetViewCompany.setOnClickListener {
                    navigate(CompanyProfileFragment.newInstance(companyId))
                }
            }
            if (isWaiting || isActive) {
                binding.btnAppDetHub.text = if (isWaiting) "View Waiting Connection" else "Open Internship Hub"
                binding.btnAppDetHub.visibility = View.VISIBLE
                binding.btnAppDetHub.setOnClickListener {
                    navigate(InternshipProgressHubFragment.newInstance(connId, "STUDENT"))
                }
            }
            if (isActive) showProgressChat(connId, "STUDENT")
            if (supervisorUid.isNotBlank()) {
                binding.btnAppDetMessage.text = "Message Company"
                binding.btnAppDetMessage.visibility = View.VISIBLE
                binding.btnAppDetMessage.setOnClickListener { openDirectChat(supervisorUid, supervisorName) }
            }
        }
    }

    private fun showProgressChat(connId: String, role: String) {
        val a = requireArguments()
        binding.btnAppDetChat.visibility = View.VISIBLE
        binding.btnAppDetChat.setOnClickListener {
            authRepo.getOrCreateProgressConversation(
                connectionId = connId,
                studentUid = a.getString(A_STUDENT_UID, ""),
                studentName = a.getString(A_STUDENT_NAME, ""),
                supervisorUid = a.getString(A_SUP_UID, ""),
                supervisorName = a.getString(A_SUP_NAME, ""),
                instructorUid = a.getString(A_INS_UID, ""),
                instructorName = a.getString(A_INS_NAME, ""),
                onSuccess = { convId ->
                    val progressTitle = "${a.getString(A_TITLE, "").ifBlank { "Internship" }} - Internship Chat"
                    val subtitle = "${a.getString(A_STUDENT_NAME, "")} • ${a.getString(A_COMPANY_NAME, "")}"
                    navigate(ChatFragment.newInstance(convId, "GROUP", progressTitle, subtitle))
                },
                onFailure = { msg ->
                    if (!isAdded) return@getOrCreateProgressConversation
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun openDirectChat(otherUid: String, otherName: String) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val myName = AppSessionCache.currentUser?.fullName ?: AppSessionCache.currentCompany?.name ?: ""
        authRepo.getOrCreateConversation(
            uidA = myUid, nameA = myName,
            uidB = otherUid, nameB = otherName,
            onSuccess = { convId -> navigate(ChatFragment.newInstance(convId, otherUid, otherName)) },
            onFailure = { msg ->
                if (!isAdded) return@getOrCreateConversation
                Toast.makeText(requireContext(), "Could not open chat: $msg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmAccept(appId: String, studentName: String, reviewerUid: String) {
        // Company supervisor sets the internship period (start/end) at accept time.
        InternshipPeriodDialog.show(requireContext()) { startMs, endMs, requiredReports ->
            authRepo.acceptInternshipApplication(appId, reviewerUid,
                startDateMs = startMs,
                endDateMs = endMs,
                requiredReportsCount = requiredReports,
                onSuccess = {
                    Toast.makeText(requireContext(), "Application accepted.", Toast.LENGTH_SHORT).show()
                    notifyActioned(); dismiss()
                },
                onFailure = { msg -> if (isAdded) Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show() }
            )
        }
    }

    private fun confirmReject(appId: String, studentName: String, reviewerUid: String) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for rejection (optional)"
            setPadding(48, 32, 48, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reject Application")
            .setMessage("Reject $studentName's application?")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                authRepo.rejectInternshipApplication(appId, reviewerUid, input.text?.toString()?.trim() ?: "",
                    onSuccess = {
                        Toast.makeText(requireContext(), "Application rejected.", Toast.LENGTH_SHORT).show()
                        notifyActioned(); dismiss()
                    },
                    onFailure = { msg -> if (isAdded) Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmEnd(
        connId: String,
        studentUid: String,
        studentName: String,
        instructorUid: String,
        companyId: String,
        companyName: String
    ) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for ending this internship"
            setPadding(48, 32, 48, 0)
            minLines = 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("End Internship")
            .setMessage("End $studentName's internship? The student and instructor will be notified. This cannot be undone.")
            .setView(input)
            .setPositiveButton("End Internship") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                if (reason.isEmpty()) {
                    if (isAdded) Toast.makeText(requireContext(), "Please enter a reason.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                authRepo.endInternshipConnection(
                    connectionId = connId,
                    studentUid = studentUid,
                    instructorUid = instructorUid,
                    reason = reason,
                    companyId = companyId,
                    companyName = companyName,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Internship ended. Student and instructor notified.", Toast.LENGTH_SHORT).show()
                        notifyActioned(); dismiss()
                    },
                    onFailure = { msg -> if (isAdded) Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun notifyActioned() {
        (parentFragment as? OnActionListener ?: activity as? OnActionListener)?.onApplicationActioned()
    }

    private fun navigate(fragment: Fragment) {
        val act = activity
        dismiss()
        when (act) {
            is StudentDashBoard -> act.openDetail(fragment)
            is CompanyDashBoard -> act.openDetail(fragment)
        }
    }

    private fun bindRow(row: com.example.intertrack.databinding.ItemAppDetailRowBinding, label: String, value: String) {
        row.tvDetailLabel.text = label
        row.tvDetailValue.text = value.ifBlank { "—" }
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun formatDate(ms: Long): String =
        if (ms > 0L) SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms)) else "—"

    private fun applyStatusBadge(status: String) {
        val (label, bg, color) = when (status) {
            "PENDING" -> Triple("Pending", com.example.intertrack.R.drawable.bg_status_pending, "#92400E")
            "ACCEPTED" -> Triple("Accepted", com.example.intertrack.R.drawable.bg_status_approved, "#166534")
            "ACTIVE" -> Triple("Active", com.example.intertrack.R.drawable.bg_status_approved, "#166534")
            "WAITING_INSTRUCTOR_CONNECTION" -> Triple("Waiting Instructor", com.example.intertrack.R.drawable.bg_status_pending, "#C2410C")
            "REJECTED" -> Triple("Rejected", com.example.intertrack.R.drawable.bg_status_blocked, "#FFFFFF")
            "ENDED" -> Triple("Ended", com.example.intertrack.R.drawable.bg_status_blocked, "#FFFFFF")
            else -> Triple(status, com.example.intertrack.R.drawable.bg_status_pending, "#92400E")
        }
        binding.tvAppDetStatus.text = label
        binding.tvAppDetStatus.setBackgroundResource(bg)
        binding.tvAppDetStatus.setTextColor(android.graphics.Color.parseColor(color))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
