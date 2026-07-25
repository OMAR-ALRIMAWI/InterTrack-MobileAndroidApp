package com.example.intertrack.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentApplicantDetailSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApplicantDetailSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentApplicantDetailSheetBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    interface OnActionListener {
        fun onApplicationActioned()
    }

    companion object {
        private const val ARG_APP_ID        = "app_id"
        private const val ARG_STUDENT_NAME  = "student_name"
        private const val ARG_STUDENT_EMAIL = "student_email"
        private const val ARG_UNIVERSITY    = "university"
        private const val ARG_MAJOR         = "major"
        private const val ARG_YEAR          = "year"
        private const val ARG_SKILLS        = "skills"
        private const val ARG_OFFER_TITLE   = "offer_title"
        private const val ARG_STATUS        = "status"
        private const val ARG_REJECTION     = "rejection_reason"
        private const val ARG_APPLIED_MS    = "applied_ms"
        private const val ARG_REVIEWER_UID  = "reviewer_uid"

        fun newInstance(
            applicationId: String,
            studentName: String,
            studentEmail: String,
            university: String,
            major: String,
            year: String,
            skills: String,
            offerTitle: String,
            status: String,
            rejectionReason: String,
            appliedAtMs: Long,
            reviewerUid: String
        ): ApplicantDetailSheet {
            return ApplicantDetailSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_APP_ID, applicationId)
                    putString(ARG_STUDENT_NAME, studentName)
                    putString(ARG_STUDENT_EMAIL, studentEmail)
                    putString(ARG_UNIVERSITY, university)
                    putString(ARG_MAJOR, major)
                    putString(ARG_YEAR, year)
                    putString(ARG_SKILLS, skills)
                    putString(ARG_OFFER_TITLE, offerTitle)
                    putString(ARG_STATUS, status)
                    putString(ARG_REJECTION, rejectionReason)
                    putLong(ARG_APPLIED_MS, appliedAtMs)
                    putString(ARG_REVIEWER_UID, reviewerUid)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicantDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val applicationId = args.getString(ARG_APP_ID, "")
        val studentName   = args.getString(ARG_STUDENT_NAME, "")
        val email         = args.getString(ARG_STUDENT_EMAIL, "")
        val university    = args.getString(ARG_UNIVERSITY, "")
        val major         = args.getString(ARG_MAJOR, "")
        val year          = args.getString(ARG_YEAR, "")
        val skills        = args.getString(ARG_SKILLS, "")
        val offerTitle    = args.getString(ARG_OFFER_TITLE, "")
        val status        = args.getString(ARG_STATUS, "PENDING")
        val rejection     = args.getString(ARG_REJECTION, "")
        val appliedMs     = args.getLong(ARG_APPLIED_MS, 0L)
        val reviewerUid   = args.getString(ARG_REVIEWER_UID, "")

        val nameParts = studentName.trim().split(" ").filter { it.isNotEmpty() }
        binding.tvApplicantInitials.text = when {
            nameParts.size >= 2 -> "${nameParts[0].first()}${nameParts[1].first()}".uppercase()
            nameParts.size == 1 -> nameParts[0].take(2).uppercase()
            else -> "?"
        }
        binding.tvApplicantName.text = studentName.ifBlank { "Student" }
        binding.tvApplicantOfferTitle.text = offerTitle
        binding.tvApplicantEmail.text = email.ifBlank { "—" }
        binding.tvApplicantUniversity.text = university.ifBlank { "—" }
        binding.tvApplicantMajor.text = major.ifBlank { "—" }
        binding.tvApplicantYear.text = year.ifBlank { "—" }
        binding.tvApplicantSkills.text = skills.ifBlank { "—" }

        if (appliedMs > 0L) {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            binding.tvApplicantDate.text = sdf.format(Date(appliedMs))
        } else {
            binding.tvApplicantDate.text = "—"
        }

        applyStatusBadge(status)

        if (status == "REJECTED" && rejection.isNotBlank()) {
            binding.tvApplicantRejectionReason.text = "Reason: $rejection"
            binding.tvApplicantRejectionReason.visibility = View.VISIBLE
        }

        if (status == "PENDING") {
            binding.llApplicantActions.visibility = View.VISIBLE
            binding.btnApplicantAccept.setOnClickListener { confirmAccept(applicationId, studentName, reviewerUid) }
            binding.btnApplicantReject.setOnClickListener { confirmReject(applicationId, studentName, reviewerUid) }
        }
    }

    private fun confirmAccept(applicationId: String, studentName: String, reviewerUid: String) {
        // Company supervisor sets the internship period (start/end) at accept time.
        InternshipPeriodDialog.show(requireContext()) { startMs, endMs, requiredReports ->
            doAccept(applicationId, reviewerUid, startMs, endMs, requiredReports)
        }
    }

    private fun doAccept(
        applicationId: String,
        reviewerUid: String,
        startMs: Long,
        endMs: Long,
        requiredReports: Int
    ) {
        binding.btnApplicantAccept.isEnabled = false
        binding.btnApplicantReject.isEnabled = false
        authRepo.acceptInternshipApplication(
            applicationId = applicationId,
            reviewerUid = reviewerUid,
            startDateMs = startMs,
            endDateMs = endMs,
            requiredReportsCount = requiredReports,
            onSuccess = {
                if (_binding == null) return@acceptInternshipApplication
                Toast.makeText(requireContext(), "Application accepted.", Toast.LENGTH_SHORT).show()
                (parentFragment as? OnActionListener
                    ?: activity as? OnActionListener)?.onApplicationActioned()
                dismiss()
            },
            onFailure = { msg ->
                if (_binding == null) return@acceptInternshipApplication
                binding.btnApplicantAccept.isEnabled = true
                binding.btnApplicantReject.isEnabled = true
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun confirmReject(applicationId: String, studentName: String, reviewerUid: String) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for rejection (optional)"
            setPadding(48, 32, 48, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reject Application")
            .setMessage("Reject $studentName's application?")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                doReject(applicationId, reason, reviewerUid)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doReject(applicationId: String, reason: String, reviewerUid: String) {
        binding.btnApplicantAccept.isEnabled = false
        binding.btnApplicantReject.isEnabled = false
        authRepo.rejectInternshipApplication(
            applicationId = applicationId,
            reviewerUid = reviewerUid,
            reason = reason,
            onSuccess = {
                if (_binding == null) return@rejectInternshipApplication
                Toast.makeText(requireContext(), "Application rejected.", Toast.LENGTH_SHORT).show()
                (parentFragment as? OnActionListener
                    ?: activity as? OnActionListener)?.onApplicationActioned()
                dismiss()
            },
            onFailure = { msg ->
                if (_binding == null) return@rejectInternshipApplication
                binding.btnApplicantAccept.isEnabled = true
                binding.btnApplicantReject.isEnabled = true
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun applyStatusBadge(status: String) {
        when (status) {
            "PENDING" -> {
                binding.tvApplicantStatus.text = "Pending"
                binding.tvApplicantStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_pending)
                binding.tvApplicantStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
            }
            "ACCEPTED" -> {
                binding.tvApplicantStatus.text = "Accepted"
                binding.tvApplicantStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_approved)
                binding.tvApplicantStatus.setTextColor(android.graphics.Color.parseColor("#166534"))
            }
            "REJECTED" -> {
                binding.tvApplicantStatus.text = "Rejected"
                binding.tvApplicantStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_blocked)
                binding.tvApplicantStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }
            else -> {
                binding.tvApplicantStatus.text = status
                binding.tvApplicantStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_pending)
                binding.tvApplicantStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
