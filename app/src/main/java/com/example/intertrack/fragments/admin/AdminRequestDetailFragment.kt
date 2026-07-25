package com.example.intertrack.fragments.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.AdminDashboardActivity
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.CompanyVerification
import com.example.intertrack.data.model.StudentVerification
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.FragmentAdminRequestDetailBinding
import com.google.firebase.auth.FirebaseAuth

class AdminRequestDetailFragment : Fragment() {

    companion object {
        private const val ARG_UID = "uid"
        private const val ARG_NAME = "name"
        private const val ARG_EMAIL = "email"
        private const val ARG_ROLE = "role"

        fun newInstance(uid: String, fullName: String, email: String, role: String) =
            AdminRequestDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_UID, uid)
                    putString(ARG_NAME, fullName)
                    putString(ARG_EMAIL, email)
                    putString(ARG_ROLE, role)
                }
            }
    }

    private var _binding: FragmentAdminRequestDetailBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    private var uid = ""
    private var fullName = ""
    private var email = ""
    private var role = ""
    private var actionInProgress = false
    // Snapshot of the loaded verification (student or instructor); null until the fetch returns.
    private var loadedVerification: StudentVerification? = null
    // Snapshot of the loaded company verification; null until the fetch returns / not applicable.
    private var loadedCompanyVerification: CompanyVerification? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminRequestDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uid = arguments?.getString(ARG_UID) ?: ""
        fullName = arguments?.getString(ARG_NAME) ?: ""
        email = arguments?.getString(ARG_EMAIL) ?: ""
        role = arguments?.getString(ARG_ROLE) ?: ""

        binding.tvDetailName.text = fullName
        binding.tvDetailEmail.text = email
        binding.tvDetailRole.text = UserRole.fromString(role)?.displayName() ?: role

        val initials = fullName.trim().split(" ").filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
        binding.tvDetailInitials.text = initials.ifBlank { "?" }

        binding.btnDetailBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // All three verified roles now show their real verification card — no more generic "no
        // details" for company supervisors.
        when (role) {
            UserRole.STUDENT.value, UserRole.INSTRUCTOR.value -> {
                binding.verificationSection.visibility = View.VISIBLE
                binding.companyVerificationSection.visibility = View.GONE
                loadVerificationDetails()
            }
            UserRole.COMPANY_SUPERVISOR.value -> {
                binding.verificationSection.visibility = View.GONE
                binding.companyVerificationSection.visibility = View.VISIBLE
                loadCompanyVerificationDetails()
            }
            else -> {
                binding.verificationSection.visibility = View.GONE
                binding.companyVerificationSection.visibility = View.GONE
                binding.tvNoVerificationData.visibility = View.VISIBLE
                binding.tvNoVerificationData.text = "No additional verification details for this role."
            }
        }

        binding.btnApproveRequest.setOnClickListener { if (!actionInProgress) handleApprove() }
        binding.btnRejectRequest.setOnClickListener { if (!actionInProgress) handleReject() }
    }

    private fun loadVerificationDetails() {
        binding.verificationLoading.visibility = View.VISIBLE
        binding.verificationContent.visibility = View.GONE
        binding.tvNoVerificationData.visibility = View.GONE

        authRepo.fetchStudentVerification(
            uid = uid,
            onSuccess = { verification ->
                if (_binding == null) return@fetchStudentVerification
                binding.verificationLoading.visibility = View.GONE
                loadedVerification = verification
                if (verification == null) {
                    binding.tvNoVerificationData.visibility = View.VISIBLE
                    binding.tvNoVerificationData.text = if (role == UserRole.INSTRUCTOR.value)
                        "Instructor has not yet submitted verification details."
                    else
                        "Student has not yet submitted verification details."
                } else {
                    binding.verificationContent.visibility = View.VISIBLE
                    displayVerification(verification)
                }
            },
            onFailure = { message ->
                if (_binding == null) return@fetchStudentVerification
                binding.verificationLoading.visibility = View.GONE
                binding.tvNoVerificationData.visibility = View.VISIBLE
                binding.tvNoVerificationData.text = "Could not load verification details."
            }
        )
    }

    private fun loadCompanyVerificationDetails() {
        binding.companyVerificationLoading.visibility = View.VISIBLE
        binding.companyVerificationContent.visibility = View.GONE
        binding.tvNoVerificationData.visibility = View.GONE

        authRepo.fetchCompanyVerification(
            uid = uid,
            onSuccess = { verification ->
                if (_binding == null) return@fetchCompanyVerification
                binding.companyVerificationLoading.visibility = View.GONE
                loadedCompanyVerification = verification
                if (verification == null || verification.companyName.isBlank()) {
                    binding.companyVerificationContent.visibility = View.GONE
                    binding.tvNoVerificationData.visibility = View.VISIBLE
                    binding.tvNoVerificationData.text =
                        "Company supervisor has not yet submitted verification details."
                } else {
                    binding.companyVerificationContent.visibility = View.VISIBLE
                    displayCompanyVerification(verification)
                }
            },
            onFailure = {
                if (_binding == null) return@fetchCompanyVerification
                binding.companyVerificationLoading.visibility = View.GONE
                binding.companyVerificationContent.visibility = View.GONE
                binding.tvNoVerificationData.visibility = View.VISIBLE
                binding.tvNoVerificationData.text = "Could not load verification details."
            }
        )
    }

    private fun displayCompanyVerification(v: CompanyVerification) {
        binding.tvCompVerCompanyName.text = v.companyName.ifBlank { "Not provided" }
        binding.tvCompVerCompanyEmail.text = v.companyEmail.ifBlank { "Not provided" }
        binding.tvCompVerRegNumber.text = v.companyRegistrationNumber.ifBlank { "Not provided" }
        binding.tvCompVerAddress.text = v.companyAddress.ifBlank { "Not provided" }
        binding.tvCompVerPhone.text = v.companyPhone.ifBlank { "Not provided" }
        binding.tvCompVerWebsite.text = v.companyWebsite.ifBlank { "Not provided" }
        binding.tvCompVerSupervisorName.text = v.supervisorFullName.ifBlank { "Not provided" }
        binding.tvCompVerSupervisorPosition.text = v.supervisorPosition.ifBlank { "Not provided" }
        binding.tvCompVerCommercialDoc.text = "Commercial Registration: " +
            (v.commercialRegistrationFileName?.takeIf { it.isNotBlank() } ?: "Not uploaded")
        binding.tvCompVerAuthorizationDoc.text = "Authorization: " +
            (v.authorizationFileName?.takeIf { it.isNotBlank() } ?: "Not uploaded")
    }

    private fun displayVerification(v: StudentVerification) {
        binding.tvPersonalId.text = v.personalIdNumber.ifBlank { "Not provided" }
        binding.tvUniversityId.text = v.universityIdNumber.ifBlank { "Not provided" }
        binding.tvUniversity.text = if (v.university.isNotBlank()) {
            val key = UniversityUtil.keyForDisplayName(v.university)
            if (key.isNotBlank()) "${v.university} · $key" else v.university
        } else "Not provided"
        binding.tvMajor.text = v.major.ifBlank { "Not provided" }
        binding.tvAcademicYear.text = if (role == UserRole.INSTRUCTOR.value) "N/A"
            else v.academicYear.ifBlank { "Not provided" }
        binding.tvPersonalIdDoc.text =
            if (v.personalIdFileName != null) "✓ ${v.personalIdFileName}" else "Not uploaded"
        binding.tvUniversityIdDoc.text =
            if (v.universityIdFileName != null) "✓ ${v.universityIdFileName}" else "Not uploaded"
    }

    private fun handleApprove() {
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // Block instructor approval if the university verification is incomplete. Existing already-ACTIVE
        // instructors are unaffected: this only runs when admin opens a PENDING request.
        if (role == UserRole.INSTRUCTOR.value) {
            val v = loadedVerification
            val universityOk = v != null && v.university.isNotBlank() &&
                UniversityUtil.keyForDisplayName(v.university).isNotBlank()
            val proofOk = v != null && (
                (v.personalIdFileName?.isNotBlank() == true) ||
                (v.universityIdFileName?.isNotBlank() == true)
            )
            if (!universityOk || !proofOk) {
                Toast.makeText(requireContext(),
                    "Cannot approve instructor: university verification is incomplete.",
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        // Block a PENDING company supervisor when required fields or proof are missing.
        if (role == UserRole.COMPANY_SUPERVISOR.value) {
            val cv = loadedCompanyVerification
            val complete = cv != null &&
                cv.companyName.isNotBlank() &&
                cv.companyEmail.isNotBlank() &&
                cv.companyRegistrationNumber.isNotBlank() &&
                cv.commercialRegistrationFileName?.isNotBlank() == true
            if (!complete) {
                Toast.makeText(requireContext(),
                    "Cannot approve company supervisor: company verification is incomplete.",
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Approve Account")
            .setMessage("Approve $fullName's account? They will gain full access to InterTrack.")
            .setPositiveButton("Approve") { _, _ ->
                actionInProgress = true
                setButtonsEnabled(false)
                authRepo.approveUser(
                    targetUid = uid,
                    adminUid = adminUid,
                    onSuccess = {
                        Toast.makeText(requireContext(), "$fullName approved.", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    },
                    onFailure = { msg ->
                        actionInProgress = false
                        setButtonsEnabled(true)
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleReject() {
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val input = EditText(requireContext()).apply {
            hint = "Rejection reason (optional)"
            maxLines = 3
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reject Account")
            .setMessage("Reject $fullName's registration request?")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                // Pass the admin's EXACT typed reason (may be blank). The rejected user's screen
                // shows this string verbatim when present, and only falls back to a generic message
                // when it is truly blank — the DB stores the admin's actual words.
                val reason = input.text?.toString()?.trim() ?: ""
                actionInProgress = true
                setButtonsEnabled(false)
                authRepo.rejectUser(
                    targetUid = uid,
                    adminUid = adminUid,
                    reason = reason,
                    onSuccess = {
                        Toast.makeText(requireContext(), "$fullName rejected.", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    },
                    onFailure = { msg ->
                        actionInProgress = false
                        setButtonsEnabled(true)
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnApproveRequest.isEnabled = enabled
        binding.btnRejectRequest.isEnabled = enabled
        binding.btnApproveRequest.alpha = if (enabled) 1f else 0.5f
        binding.btnRejectRequest.alpha = if (enabled) 1f else 0.5f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
