package com.example.intertrack.fragments

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.StudentVerification
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.databinding.FragmentVerifyStudentBinding
import com.google.firebase.auth.FirebaseAuth

class VerifyStudentFragment : Fragment() {

    private var _binding: FragmentVerifyStudentBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    private var personalIdFileName: String? = null
    private var universityIdFileName: String? = null

    private val personalIdPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            personalIdFileName = getFileName(uri) ?: "document_selected"
            binding.tvPersonalIdFileName.text = personalIdFileName
            binding.tvPersonalIdFileName.visibility = View.VISIBLE
            binding.tvPersonalIdPickerLabel.text = "Personal ID selected"
        }
    }

    private val universityIdPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            universityIdFileName = getFileName(uri) ?: "document_selected"
            binding.tvUniversityIdFileName.text = universityIdFileName
            binding.tvUniversityIdFileName.visibility = View.VISIBLE
            binding.tvUniversityIdPickerLabel.text = "University ID selected"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }

        binding.btnVerifyBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // University is chosen from the allowed list (no free text) — tap opens a single-choice list.
        binding.etVerifyUniversity.apply {
            isFocusable = false
            isClickable = true
            keyListener = null
            setOnClickListener { showUniversityPicker() }
        }

        binding.btnUploadPersonalId.setOnClickListener {
            personalIdPicker.launch("*/*")
        }

        binding.btnUploadUniversityId.setOnClickListener {
            universityIdPicker.launch("*/*")
        }

        binding.btnSubmitVerification.setOnClickListener {
            if (validateForm()) submitVerification()
        }

        binding.btnGoToDashboard.setOnClickListener {
            (requireActivity() as? StudentDashBoard)?.apply {
                setHeaderVisible(true)
                setNavVisible(true)
                loadHome()
            }
        }

        checkExistingVerification()
    }

    private fun checkExistingVerification() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        authRepo.fetchStudentVerification(
            uid = uid,
            onSuccess = { existing ->
                if (_binding == null) return@fetchStudentVerification
                if (existing != null) {
                    binding.layoutVerifyForm.visibility = View.GONE
                    binding.layoutVerifyPending.visibility = View.VISIBLE
                } else {
                    prefillFormFromUserDoc(uid)
                }
            },
            onFailure = { /* fail silently — show form as fallback */ }
        )
    }

    private fun prefillFormFromUserDoc(uid: String) {
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                binding.etVerifyFullName.setText(user.fullName)
                binding.etVerifyUniversity.setText(user.university ?: "")
                binding.etVerifyMajor.setText(user.major ?: "")
                binding.etVerifyAcademicYear.setText(user.academicYear ?: "")
            },
            onFailure = { /* fail silently — fields stay blank */ }
        )
    }

    private fun showUniversityPicker() {
        val options = UniversityUtil.displayNames()
        val current = binding.etVerifyUniversity.text?.toString()?.trim()
        val checked = options.indexOfFirst { it.equals(current, ignoreCase = true) }
        AlertDialog.Builder(requireContext())
            .setTitle("Select University")
            .setSingleChoiceItems(options.toTypedArray(), checked) { dialog, which ->
                binding.etVerifyUniversity.setText(options[which])
                binding.etVerifyUniversity.error = null
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateForm(): Boolean {
        val name = binding.etVerifyFullName.text?.toString()?.trim()
        val university = binding.etVerifyUniversity.text?.toString()?.trim()
        val studentId = binding.etVerifyStudentId.text?.toString()?.trim()
        val major = binding.etVerifyMajor.text?.toString()?.trim()
        val email = binding.etVerifyUniEmail.text?.toString()?.trim()
        val academicYear = binding.etVerifyAcademicYear.text?.toString()?.trim()
        val phone = binding.etVerifyPhone.text?.toString()?.trim()

        if (name.isNullOrBlank()) {
            binding.etVerifyFullName.error = "Full name is required"
            binding.etVerifyFullName.requestFocus()
            return false
        }
        if (name.length < 2) {
            binding.etVerifyFullName.error = "Enter your full name (at least 2 characters)"
            binding.etVerifyFullName.requestFocus()
            return false
        }
        if (university.isNullOrBlank()) {
            binding.etVerifyUniversity.error = "University name is required"
            binding.etVerifyUniversity.requestFocus()
            return false
        }
        if (university.length < 2) {
            binding.etVerifyUniversity.error = "Enter a valid university name"
            binding.etVerifyUniversity.requestFocus()
            return false
        }
        if (studentId.isNullOrBlank()) {
            binding.etVerifyStudentId.error = "Student ID is required"
            binding.etVerifyStudentId.requestFocus()
            return false
        }
        if (studentId.length < 5) {
            binding.etVerifyStudentId.error = "Student ID must be at least 5 characters"
            binding.etVerifyStudentId.requestFocus()
            return false
        }
        if (major.isNullOrBlank()) {
            binding.etVerifyMajor.error = "Major / Department is required"
            binding.etVerifyMajor.requestFocus()
            return false
        }
        if (major.length < 2) {
            binding.etVerifyMajor.error = "Enter a valid major or department"
            binding.etVerifyMajor.requestFocus()
            return false
        }
        if (!academicYear.isNullOrBlank() && !isValidAcademicYear(academicYear)) {
            binding.etVerifyAcademicYear.error = "Enter a valid academic year, e.g. 3rd Year or 2024/2025"
            binding.etVerifyAcademicYear.requestFocus()
            return false
        }
        if (email.isNullOrBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etVerifyUniEmail.error = "Enter a valid university email address"
            binding.etVerifyUniEmail.requestFocus()
            return false
        }
        if (!phone.isNullOrBlank()) {
            val digitsOnly = phone.replace(Regex("[+\\s\\-()]"), "")
            if (!digitsOnly.all { it.isDigit() } || digitsOnly.length < 7 || digitsOnly.length > 15) {
                binding.etVerifyPhone.error = "Enter a valid phone number (7–15 digits)"
                binding.etVerifyPhone.requestFocus()
                return false
            }
        }
        if (personalIdFileName == null) {
            Toast.makeText(requireContext(), "Please select your Personal ID document", Toast.LENGTH_SHORT).show()
            return false
        }
        if (universityIdFileName == null) {
            Toast.makeText(requireContext(), "Please select your University ID card", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isValidAcademicYear(value: String): Boolean {
        if (value.length < 2) return false
        val lower = value.lowercase()
        return lower.contains("year") ||
            lower.contains("st year") || lower.contains("nd year") ||
            lower.contains("rd year") || lower.contains("th year") ||
            Regex("\\d{4}[/\\-–]\\d{4}").containsMatchIn(value) ||
            Regex("\\d{4}[/\\-–]\\d{2}").containsMatchIn(value) ||
            Regex("^[1-4](st|nd|rd|th)\\s", RegexOption.IGNORE_CASE).containsMatchIn(value + " ")
    }

    private fun submitVerification() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.btnSubmitVerification.isEnabled = false
        binding.btnSubmitVerification.text = "Submitting…"

        val university = binding.etVerifyUniversity.text?.toString()?.trim() ?: ""
        val studentId = binding.etVerifyStudentId.text?.toString()?.trim() ?: ""
        val major = binding.etVerifyMajor.text?.toString()?.trim() ?: ""
        val academicYear = binding.etVerifyAcademicYear.text?.toString()?.trim() ?: ""

        val verification = StudentVerification(
            uid = uid,
            universityIdNumber = studentId,
            university = university,
            major = major,
            academicYear = academicYear,
            personalIdFileName = personalIdFileName,
            universityIdFileName = universityIdFileName,
            personalIdSelected = true,
            universityIdSelected = true,
            status = "PENDING"
        )

        authRepo.saveStudentVerification(
            verification = verification,
            onSuccess = {
                if (_binding == null) return@saveStudentVerification
                authRepo.updateUserProfile(
                    uid = uid,
                    updates = mapOf(
                        "university" to university,
                        "universityKey" to UniversityUtil.keyForDisplayName(university),
                        "major" to major,
                        "academicYear" to academicYear
                    ),
                    onSuccess = {},
                    onFailure = {}
                )
                binding.btnSubmitVerification.isEnabled = true
                binding.btnSubmitVerification.text = "Submit Verification Request"
                binding.layoutVerifyForm.visibility = View.GONE
                binding.layoutVerifyPending.visibility = View.VISIBLE
            },
            onFailure = { msg ->
                if (_binding == null) return@saveStudentVerification
                binding.btnSubmitVerification.isEnabled = true
                binding.btnSubmitVerification.text = "Submit Verification Request"
                val userMsg = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Submission failed. Please log out and sign in again, then try once more. If the problem persists, contact the administrator."
                } else {
                    "Submission failed. Please check your connection and try again."
                }
                Toast.makeText(requireContext(), userMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            null
        }
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
        // Header is re-shown by the destination fragment's onResume (avoids duplicated header).
        _binding = null
    }
}
