package com.example.intertrack.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.data.model.WeekUtil
import com.example.intertrack.databinding.FragmentReportDetailBinding
import com.google.firebase.auth.FirebaseAuth

class ReportDetailFragment : Fragment() {

    private var _binding: FragmentReportDetailBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var currentReport: FirestoreReport? = null

    companion object {
        private const val ARG_REPORT_ID = "report_id"

        fun newInstance(reportId: String): ReportDetailFragment {
            return ReportDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_REPORT_ID, reportId) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideHostUI()

        binding.btnReportDetailBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadReport()

        binding.btnMarkReviewed.setOnClickListener { submitReview("REVIEWED") }
        binding.btnRequestRevision.setOnClickListener { submitReview("REVISION_REQUESTED") }
        binding.btnSaveSupervisorFeedback.setOnClickListener { saveSupervisorFeedback() }

        binding.btnEditReport.setOnClickListener {
            val report = currentReport ?: return@setOnClickListener
            (requireActivity() as? StudentDashBoard)?.openDetail(
                CreateReportFragment.newInstanceForEdit(report.reportId)
            )
        }
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "ReportDetailFragment -> Loading")
        binding.progressReportDetail.visibility = View.VISIBLE
        binding.reportDetailContent.visibility = View.GONE
        binding.tvReportDetailError.visibility = View.GONE

        // Clear visible fields
        binding.tvReportDetailTitle.text = ""
        binding.tvReportDetailContent.text = ""
        binding.tvReportDetailMeta.text = ""
        binding.tvReportDetailSkills.text = ""
        binding.tvReportDetailChallenges.text = ""
        binding.tvInstructorFeedbackReadonly.text = ""
        binding.tvSupervisorFeedbackReadonly.text = ""
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "ReportDetailFragment -> Content")
        binding.progressReportDetail.visibility = View.GONE
        binding.reportDetailContent.visibility = View.VISIBLE
        binding.tvReportDetailError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "ReportDetailFragment -> Empty")
        showErrorState("Report not found.")
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "ReportDetailFragment -> Error: $message")
        binding.progressReportDetail.visibility = View.GONE
        binding.reportDetailContent.visibility = View.GONE
        binding.tvReportDetailError.visibility = View.VISIBLE
        binding.tvReportDetailError.text = message
    }

    private fun loadReport() {
        val reportId = arguments?.getString(ARG_REPORT_ID) ?: return
        
        showLoadingState()

        authRepo.getReportById(
            reportId = reportId,
            onSuccess = { report ->
                if (_binding == null) return@getReportById
                if (report == null) {
                    showEmptyState()
                    return@getReportById
                }
                currentReport = report
                bindReport(report)
                showContentState()
            },
            onFailure = { msg ->
                if (_binding == null) return@getReportById
                showErrorState(msg ?: "Failed to load report")
            }
        )
    }

    private fun bindReport(report: FirestoreReport) {
        binding.tvReportDetailTitle.text = report.reportTitle.ifBlank { "Untitled Report" }
        applyStatusBadge(report.status)

        val isInstructorHost = requireActivity() is InstructorDashBoard
        val isSupervisorHost = requireActivity() is CompanyDashBoard
        val isStudentHost = requireActivity() is StudentDashBoard

        if (isInstructorHost || isSupervisorHost) {
            binding.tvReportDetailStudent.visibility = View.VISIBLE
            binding.tvReportDetailStudent.text = report.studentName.ifBlank { "Student" }
        } else {
            binding.tvReportDetailStudent.visibility = View.GONE
        }

        val metaParts = mutableListOf<String>()
        if (report.reportWeekNumber > 0) metaParts.add("Week ${report.reportWeekNumber}")
        val startMs = report.periodStart?.toDate()?.time
        val endMs = report.periodEnd?.toDate()?.time
        if (startMs != null && endMs != null) {
            metaParts.add("${WeekUtil.formatDate(startMs)} – ${WeekUtil.formatDate(endMs)}")
        } else if (report.reportPeriod.isNotBlank()) {
            metaParts.add(report.reportPeriod)
        }
        report.deadlineDate?.toDate()?.time?.let { metaParts.add("Deadline ${WeekUtil.formatDate(it)}") }
        if (report.companyName.isNotBlank()) metaParts.add(report.companyName)
        if (report.hoursWorked.isNotBlank()) metaParts.add("${report.hoursWorked}h")
        binding.tvReportDetailMeta.text = metaParts.joinToString(" · ").ifBlank { "—" }

        binding.tvReportDetailContent.text = report.reportContent.ifBlank { "No content provided." }

        if (report.learnedSkills.isNotBlank()) {
            binding.tvReportDetailSkillsLabel.visibility = View.VISIBLE
            binding.tvReportDetailSkills.visibility = View.VISIBLE
            binding.tvReportDetailSkills.text = report.learnedSkills
        }

        if (report.challenges.isNotBlank()) {
            binding.tvReportDetailChallengesLabel.visibility = View.VISIBLE
            binding.tvReportDetailChallenges.visibility = View.VISIBLE
            binding.tvReportDetailChallenges.text = report.challenges
        }

        bindAttachment(report)

        binding.tvInstructorFeedbackReadonly.text =
            report.instructorFeedback.ifBlank { "No feedback yet." }
        binding.tvSupervisorFeedbackReadonly.text =
            report.supervisorFeedback.ifBlank { "No feedback yet." }

        binding.tvSupervisorFeedbackLabel.text = report.companyName.ifBlank { "Company Supervisor" }
        binding.tvInstructorFeedbackLabel.text = "Instructor"
        if (report.instructorUid.isNotBlank()) {
            authRepo.fetchUserDocument(report.instructorUid, onSuccess = { user ->
                if (_binding != null && user.fullName.isNotBlank())
                    binding.tvInstructorFeedbackLabel.text = user.fullName
            }, onFailure = {})
        }

        if (isInstructorHost) {
            binding.llInstructorReviewControls.visibility = View.VISIBLE
            binding.etInstructorFeedback.setText(report.instructorFeedback)
        } else {
            binding.llInstructorReviewControls.visibility = View.GONE
        }

        if (isSupervisorHost) {
            binding.llSupervisorFeedbackControls.visibility = View.VISIBLE
            binding.etSupervisorFeedback.setText(report.supervisorFeedback)
        } else {
            binding.llSupervisorFeedbackControls.visibility = View.GONE
        }

        if (isStudentHost && (report.status == "SUBMITTED" || report.status == "LATE" || report.status == "REVISION_REQUESTED")) {
            binding.btnEditReport.visibility = View.VISIBLE
        } else {
            binding.btnEditReport.visibility = View.GONE
        }
    }

    /**
     * Compact attachment card. Reports store attachment metadata only (no Firebase Storage), so an
     * openable URL is present only if a real downloadUrl/storageUrl was ever saved. When there is no
     * URL the card is shown but tapping it explains the file preview is unavailable.
     */
    private fun bindAttachment(report: FirestoreReport) {
        if (report.attachedFileName.isBlank()) {
            binding.llReportDetailAttachment.visibility = View.GONE
            return
        }

        binding.llReportDetailAttachment.visibility = View.VISIBLE
        binding.tvAttachmentName.text = report.attachedFileName

        val url = report.downloadUrl.ifBlank { report.storageUrl }.trim()
        val typeLabel = report.attachedFileMimeType
            .substringAfterLast('/', "")
            .uppercase()
            .ifBlank { "FILE" }

        if (url.isNotBlank()) {
            binding.tvAttachmentMeta.text = "$typeLabel · Tap to open"
            binding.ivAttachmentOpen.visibility = View.VISIBLE
            binding.attachmentContainer.setOnClickListener { openAttachment(url) }
        } else {
            binding.tvAttachmentMeta.text = "Attachment record only - file preview unavailable"
            binding.ivAttachmentOpen.visibility = View.GONE
            binding.attachmentContainer.setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    "File preview is not available. Only attachment record was saved.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openAttachment(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No app found to open this file.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Could not open the attachment.", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyStatusBadge(status: String) {
        when (status) {
            "REVIEWED" -> {
                binding.tvReportDetailStatus.text = "Reviewed"
                binding.tvReportDetailStatus.setBackgroundResource(R.drawable.bg_status_approved)
                binding.tvReportDetailStatus.setTextColor(Color.parseColor("#166534"))
            }
            "REVISION_REQUESTED" -> {
                binding.tvReportDetailStatus.text = "Revision Requested"
                binding.tvReportDetailStatus.setBackgroundResource(R.drawable.bg_status_needs_mod)
                binding.tvReportDetailStatus.setTextColor(Color.parseColor("#DC2626"))
            }
            "LATE" -> {
                binding.tvReportDetailStatus.text = "Late"
                binding.tvReportDetailStatus.setBackgroundResource(R.drawable.bg_status_needs_mod)
                binding.tvReportDetailStatus.setTextColor(Color.parseColor("#DC2626"))
            }
            else -> {
                binding.tvReportDetailStatus.text = "Submitted"
                binding.tvReportDetailStatus.setBackgroundResource(R.drawable.bg_status_pending)
                binding.tvReportDetailStatus.setTextColor(Color.parseColor("#92400E"))
            }
        }
    }

    private fun submitReview(status: String) {
        val report = currentReport ?: return
        val reviewerUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val feedback = binding.etInstructorFeedback.text?.toString()?.trim() ?: ""

        authRepo.reviewReport(
            reportId = report.reportId,
            studentUid = report.studentUid,
            status = status,
            feedback = feedback,
            reviewerUid = reviewerUid,
            onSuccess = {
                if (_binding == null) return@reviewReport
                Toast.makeText(requireContext(), "Report review saved.", Toast.LENGTH_SHORT).show()
                loadReport()
            },
            onFailure = { msg ->
                if (_binding == null) return@reviewReport
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun saveSupervisorFeedback() {
        val report = currentReport ?: return
        val feedback = binding.etSupervisorFeedback.text?.toString()?.trim() ?: ""

        authRepo.addSupervisorFeedback(
            reportId = report.reportId,
            feedback = feedback,
            onSuccess = {
                if (_binding == null) return@addSupervisorFeedback
                Toast.makeText(requireContext(), "Feedback saved.", Toast.LENGTH_SHORT).show()
                loadReport()
            },
            onFailure = { msg ->
                if (_binding == null) return@addSupervisorFeedback
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        hideHostUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideHostUI() {
        (requireActivity() as? StudentDashBoard)?.apply    { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? InstructorDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? CompanyDashBoard)?.apply    { setHeaderVisible(false); setNavVisible(false) }
    }
}
