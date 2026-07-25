package com.example.intertrack.fragments

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreDocumentMeta
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.data.model.WeekUtil
import com.example.intertrack.databinding.FragmentCreateReportBinding
import com.google.firebase.auth.FirebaseAuth

class CreateReportFragment : Fragment() {

    private var _binding: FragmentCreateReportBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var pendingDocumentName: String? = null
    private var editingReportId: String? = null
    private var pendingInternshipInfo: FirestoreReport? = null

    // Weekly period gating state
    private var activeConn: FirestoreInternshipConnection? = null
    private var currentWeek: Int = 0
    private var isLateSubmission: Boolean = false
    private var canSubmit: Boolean = true

    // Real Android document picker (Activity Result API) — record-only, no upload/Storage.
    // We keep only the selected file's display name; the Uri is not persisted.
    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (_binding == null) return@registerForActivityResult
        if (uri != null) {
            val name = resolveFileName(uri)
            pendingDocumentName = name
            binding.tvAttachedFileName.text = "Selected file: $name"
        }
        // uri == null → user cancelled: leave the current selection untouched (no crash).
    }

    private val documentMimeTypes = arrayOf(
        "application/pdf",
        "image/*",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    companion object {
        private const val ARG_REPORT_ID = "edit_report_id"

        fun newInstanceForEdit(reportId: String): CreateReportFragment {
            return CreateReportFragment().apply {
                arguments = Bundle().apply { putString(ARG_REPORT_ID, reportId) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        editingReportId = arguments?.getString(ARG_REPORT_ID)
        // Company + date are read-only: company comes from the internship; date is auto-filled.
        binding.etReportCompany.isEnabled = false
        binding.etReportDate.isEnabled = false
        binding.etReportDate.isFocusable = false

        if (editingReportId != null) {
            loadExistingReport(editingReportId!!)
        } else {
            // Auto-fill today's date as yyyy/MM/dd — the student never types it.
            binding.etReportDate.setText(
                java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
            )
            prefillInternshipInfo()
        }

        binding.btnCreateReportBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnCancelReport.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnAttachFile.setOnClickListener {
            documentPicker.launch(documentMimeTypes)
        }

        binding.btnSubmitReport.setOnClickListener {
            if (validateForm()) {
                if (editingReportId != null) updateReport() else submitReport()
            }
        }
    }

    /** Resolves the human-readable display name of the picked document. */
    private fun resolveFileName(uri: Uri): String {
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) { /* fall through to fallback */ }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Selected document"
    }

    private fun recordPendingDocumentIfNeeded(reportId: String) {
        val name = pendingDocumentName ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.recordDocumentSelection(
            document = FirestoreDocumentMeta(
                ownerUid = uid,
                relatedToType = "REPORT",
                relatedToId = reportId,
                documentName = name,
                documentType = name.substringAfterLast('.', "")
            ),
            onSuccess = {},
            onFailure = {}
        )
    }

    private fun prefillInternshipInfo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // Build report routing fields from the ACTIVE internship connection — the connection has the
        // CONFIRMED instructorUid/supervisorUid/companyId, whereas the application's
        // assignedInstructorUid is often blank (the student added the instructor after applying).
        // That blank instructorUid is exactly why the instructor never saw submitted reports.
        authRepo.getStudentActiveConnection(
            studentUid = uid,
            onSuccess = { conn ->
                if (_binding == null) return@getStudentActiveConnection
                if (conn != null) {
                    binding.etReportCompany.setText(conn.companyName)
                    pendingInternshipInfo = FirestoreReport(
                        internshipConnectionId = conn.connectionId,
                        companyId = conn.companyId,
                        companyName = conn.companyName,
                        internshipId = conn.internshipId,
                        internshipTitle = conn.internshipTitle,
                        instructorUid = conn.instructorUid,
                        supervisorUid = conn.supervisorUid
                    )
                    applyPeriodGating(conn, uid)
                } else {
                    prefillFromApplicationFallback(uid)
                }
            },
            onFailure = { prefillFromApplicationFallback(uid) }
        )
    }

    /**
     * Weekly submission gating from the ACTIVE connection's period. Populates the info card and
     * enables/disables Submit with a clear reason. Duplicate/week checks come from Firestore reports.
     */
    private fun applyPeriodGating(conn: FirestoreInternshipConnection, uid: String) {
        activeConn = conn
        val now = System.currentTimeMillis()
        val startMs = conn.startDate?.toDate()?.time ?: 0L
        val endMs = conn.endDate?.toDate()?.time ?: 0L
        val required = conn.requiredReportsCount

        // A completed internship never accepts new reports.
        if (conn.isCompleted()) {
            binding.tvReportPeriodInfo.text = "Internship: completed"
            binding.tvReportWeekInfo.text = "—"
            binding.tvReportCountInfo.text = "Reports submitted: ${conn.submittedReportsCount} / $required"
            setBlocked("Internship is completed.")
            return
        }

        if (!conn.hasPeriod()) {
            binding.tvReportPeriodInfo.text = "Internship period: not set"
            binding.tvReportWeekInfo.text = "—"
            binding.tvReportCountInfo.text = "Reports submitted: —"
            setBlocked("Internship period has not been set by the company yet.")
            return
        }

        binding.tvReportPeriodInfo.text =
            "Internship period: ${WeekUtil.formatDate(startMs)} – ${WeekUtil.formatDate(endMs)} · Weekly"

        authRepo.getStudentReports(
            studentUid = uid,
            onSuccess = { all ->
                if (_binding == null) return@getStudentReports
                val forConn = all.filter { it.internshipConnectionId == conn.connectionId }
                binding.tvReportCountInfo.text = "Reports submitted: ${forConn.size} / $required"

                val week = WeekUtil.currentWeek(startMs, now)
                currentWeek = week
                if (week in 1..required) {
                    binding.tvReportWeekInfo.text =
                        "Week $week of $required · Deadline: ${WeekUtil.formatDate(WeekUtil.periodEnd(startMs, week))}"
                } else {
                    binding.tvReportWeekInfo.text = "Week — · Deadline —"
                }

                when {
                    now < startMs -> setBlocked("Internship has not started yet.")
                    now > endMs -> setBlocked("Internship has ended. Report submission is closed.")
                    forConn.size >= required -> setBlocked("All required reports have been submitted.")
                    week < 1 || week > required -> setBlocked("Report submission is closed for this internship.")
                    forConn.any { it.reportWeekNumber == week } -> setBlocked("You already submitted this week's report.")
                    else -> {
                        isLateSubmission = WeekUtil.isLate(startMs, week, now)
                        setAllowed()
                    }
                }
            },
            onFailure = {
                // Can't verify existing reports — keep submit enabled; server rules still protect,
                // and the next open will reflect the saved report.
                if (_binding != null) setAllowed()
            }
        )
    }

    private fun setBlocked(reason: String) {
        canSubmit = false
        binding.tvReportBlockedReason.text = reason
        binding.tvReportBlockedReason.visibility = View.VISIBLE
        binding.btnSubmitReport.isEnabled = false
        binding.btnSubmitReport.alpha = 0.5f
    }

    private fun setAllowed() {
        canSubmit = true
        binding.tvReportBlockedReason.visibility = View.GONE
        binding.btnSubmitReport.isEnabled = true
        binding.btnSubmitReport.alpha = 1f
    }

    /** Old data fallback: no connection — use the accepted application's fields. */
    private fun prefillFromApplicationFallback(uid: String) {
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { applications ->
                if (_binding == null) return@getStudentApplications
                val accepted = applications.firstOrNull { it.status == "ACCEPTED" }
                if (accepted != null) {
                    binding.etReportCompany.setText(accepted.companyName)
                    pendingInternshipInfo = FirestoreReport(
                        internshipConnectionId = accepted.applicationId,
                        companyId = accepted.companyId,
                        companyName = accepted.companyName,
                        internshipId = accepted.offerId,
                        internshipTitle = accepted.offerTitle,
                        instructorUid = accepted.assignedInstructorUid,
                        supervisorUid = accepted.supervisorUid
                    )
                }
                // No ACTIVE connection with a period → block submission (no unlimited old reports).
                binding.tvReportPeriodInfo.text = "Internship period: not set"
                setBlocked("Internship period has not been set by the company yet.")
            },
            onFailure = { if (_binding != null) setBlocked("Internship period has not been set by the company yet.") }
        )
    }

    private fun loadExistingReport(reportId: String) {
        authRepo.getReportById(
            reportId = reportId,
            onSuccess = { report ->
                if (_binding == null || report == null) return@getReportById
                pendingInternshipInfo = report
                binding.etReportTitle.setText(report.reportTitle)
                binding.etReportDate.setText(report.reportPeriod)
                binding.etReportCompany.setText(report.companyName)
                binding.etHoursWorked.setText(report.hoursWorked)
                binding.etTasksCompleted.setText(report.reportContent)
                binding.etSkillsPracticed.setText(report.learnedSkills)
                binding.etChallengesFaced.setText(report.challenges)
                if (report.attachedFileName.isNotBlank()) {
                    pendingDocumentName = report.attachedFileName
                    binding.tvAttachedFileName.text = "Selected file: ${report.attachedFileName}"
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@getReportById
                Toast.makeText(requireContext(), "Failed to load report: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun validateForm(): Boolean {
        val title = binding.etReportTitle.text?.toString()?.trim() ?: ""
        val date = binding.etReportDate.text?.toString()?.trim() ?: ""
        val tasks = binding.etTasksCompleted.text?.toString()?.trim() ?: ""

        if (title.isEmpty()) {
            binding.tilReportTitle.error = "Report title is required"
            return false
        }
        binding.tilReportTitle.error = null

        if (date.isEmpty()) {
            binding.tilReportDate.error = "Date is required"
            return false
        }
        binding.tilReportDate.error = null

        if (tasks.isEmpty()) {
            binding.tilTasksCompleted.error = "Tasks completed is required"
            return false
        }
        if (tasks.length < 5) {
            binding.tilTasksCompleted.error = "Please describe your tasks (at least 5 characters)"
            return false
        }
        binding.tilTasksCompleted.error = null

        // Hours worked: required, numeric, greater than 0, at most 24.
        val hoursStr = binding.etHoursWorked.text?.toString()?.trim() ?: ""
        val hours = hoursStr.toDoubleOrNull()
        if (hoursStr.isEmpty()) {
            binding.tilHoursWorked.error = "Hours worked is required"
            return false
        }
        if (hours == null || hours <= 0.0 || hours > 24.0) {
            binding.tilHoursWorked.error = "Enter valid hours between 1 and 24"
            return false
        }
        binding.tilHoursWorked.error = null

        return true
    }

    private fun submitReport() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val studentName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""

        // Weekly gate: never submit when the internship rules block it.
        if (!canSubmit) {
            Toast.makeText(requireContext(),
                binding.tvReportBlockedReason.text.ifBlank { "You can't submit a report right now." },
                Toast.LENGTH_LONG).show()
            return
        }

        binding.btnSubmitReport.isEnabled = false

        val info = pendingInternshipInfo ?: FirestoreReport()
        val attachName = pendingDocumentName ?: ""
        // Weekly period stamps from the active connection.
        val startMs = activeConn?.startDate?.toDate()?.time ?: 0L
        val week = currentWeek
        val periodStartMs = if (week >= 1 && startMs > 0L) WeekUtil.periodStart(startMs, week) else 0L
        val periodEndMs = if (week >= 1 && startMs > 0L) WeekUtil.periodEnd(startMs, week) else 0L
        fun ts(ms: Long) = if (ms > 0L) com.google.firebase.Timestamp(java.util.Date(ms)) else null

        val report = FirestoreReport(
            internshipConnectionId = info.internshipConnectionId,
            attachedFileName = attachName,
            attachedFileMimeType = if (attachName.isNotBlank()) attachName.substringAfterLast('.', "") else "",
            reportWeekNumber = week,
            periodStart = ts(periodStartMs),
            periodEnd = ts(periodEndMs),
            deadlineDate = ts(periodEndMs),
            status = if (isLateSubmission) "LATE" else "SUBMITTED",
            studentUid = uid,
            studentName = studentName,
            companyId = info.companyId,
            companyName = binding.etReportCompany.text?.toString()?.trim() ?: info.companyName,
            internshipId = info.internshipId,
            internshipTitle = info.internshipTitle,
            instructorUid = info.instructorUid,
            supervisorUid = info.supervisorUid,
            reportTitle = binding.etReportTitle.text?.toString()?.trim() ?: "",
            reportPeriod = binding.etReportDate.text?.toString()?.trim() ?: "",
            reportContent = buildContent(),
            challenges = binding.etChallengesFaced.text?.toString()?.trim() ?: "",
            learnedSkills = binding.etSkillsPracticed.text?.toString()?.trim() ?: "",
            hoursWorked = binding.etHoursWorked.text?.toString()?.trim() ?: ""
        )

        authRepo.submitReport(
            report = report,
            onSuccess = { newReportId ->
                if (_binding == null) return@submitReport
                recordPendingDocumentIfNeeded(newReportId)
                Toast.makeText(requireContext(), "Report submitted successfully!", Toast.LENGTH_LONG).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            },
            onFailure = { msg ->
                if (_binding == null) return@submitReport
                binding.btnSubmitReport.isEnabled = true
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun updateReport() {
        val reportId = editingReportId ?: return
        binding.btnSubmitReport.isEnabled = false

        val updates = mutableMapOf<String, Any?>(
            "reportTitle" to (binding.etReportTitle.text?.toString()?.trim() ?: ""),
            "reportPeriod" to (binding.etReportDate.text?.toString()?.trim() ?: ""),
            "reportContent" to buildContent(),
            "challenges" to (binding.etChallengesFaced.text?.toString()?.trim() ?: ""),
            "learnedSkills" to (binding.etSkillsPracticed.text?.toString()?.trim() ?: ""),
            "hoursWorked" to (binding.etHoursWorked.text?.toString()?.trim() ?: ""),
            "status" to "SUBMITTED"
        )
        pendingDocumentName?.let { name ->
            updates["attachedFileName"] = name
            updates["attachedFileMimeType"] = name.substringAfterLast('.', "")
        }

        authRepo.updateReport(
            reportId = reportId,
            updates = updates,
            onSuccess = {
                if (_binding == null) return@updateReport
                recordPendingDocumentIfNeeded(reportId)
                Toast.makeText(requireContext(), "Report updated successfully!", Toast.LENGTH_LONG).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            },
            onFailure = { msg ->
                if (_binding == null) return@updateReport
                binding.btnSubmitReport.isEnabled = true
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun buildContent(): String {
        val tasks = binding.etTasksCompleted.text?.toString()?.trim() ?: ""
        val notes = binding.etProgressNotes.text?.toString()?.trim() ?: ""
        return if (notes.isNotBlank()) "$tasks\n\nProgress notes: $notes" else tasks
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Header is re-shown by the destination fragment's onResume (restoring here caused a
        // duplicated header/background when moving between full-screen detail fragments).
        _binding = null
    }
}
