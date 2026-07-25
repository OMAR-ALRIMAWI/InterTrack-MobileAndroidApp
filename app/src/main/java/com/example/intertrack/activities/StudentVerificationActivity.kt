package com.example.intertrack.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.StudentVerification
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityStudentVerificationBinding

class StudentVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UID = "svf_uid"
        const val EXTRA_EMAIL = "svf_email"
        const val EXTRA_FULL_NAME = "svf_full_name"
        const val EXTRA_ROLE = "svf_role"

        fun newIntent(context: Context, user: User): Intent =
            Intent(context, StudentVerificationActivity::class.java).apply {
                putExtra(EXTRA_UID, user.uid)
                putExtra(EXTRA_EMAIL, user.email)
                putExtra(EXTRA_FULL_NAME, user.fullName)
                putExtra(EXTRA_ROLE, user.role)
            }
    }

    private lateinit var binding: ActivityStudentVerificationBinding
    private val authRepo = FirebaseAuthRepository()

    private lateinit var uid: String
    private lateinit var email: String
    private lateinit var fullName: String
    private lateinit var role: String

    private var personalIdFileName: String? = null
    private var universityIdFileName: String? = null
    private var isSubmitting = false

    private val personalIdPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            personalIdFileName = getFileName(uri) ?: "document_selected"
            binding.tvPersonalIdFileName.text = personalIdFileName
            binding.btnUploadPersonalId.text = "Change Personal ID"
        }
    }

    private val universityIdPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            universityIdFileName = getFileName(uri) ?: "document_selected"
            binding.tvUniversityIdFileName.text = universityIdFileName
            binding.btnUploadUniversityId.text = "Change University ID"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = intent.getStringExtra(EXTRA_UID) ?: ""
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        fullName = intent.getStringExtra(EXTRA_FULL_NAME) ?: ""
        role = intent.getStringExtra(EXTRA_ROLE) ?: ""

        adaptFormForRole()

        // University is chosen from the allowed list — no free typing.
        binding.etUniversity.setOnClickListener { showUniversityPicker() }

        binding.btnUploadPersonalId.setOnClickListener {
            personalIdPicker.launch("*/*")
        }

        binding.btnUploadUniversityId.setOnClickListener {
            universityIdPicker.launch("*/*")
        }

        binding.btnSubmitVerification.setOnClickListener {
            if (!isSubmitting) submitVerification()
        }
    }

    /** Applies role-specific labels/visibility. Layout stays a single screen for both roles. */
    private fun adaptFormForRole() {
        if (role == UserRole.INSTRUCTOR.value) {
            binding.tvVerificationTitle.text = "Academic Supervisor Verification"
            binding.tvUniversityIdLabel.text = "Staff / Employee ID Number *"
            binding.etUniversityId.hint = "Enter your staff / employee ID"
            binding.tvMajorLabel.text = "Department *"
            binding.etMajor.hint = "e.g. Computer Engineering"
            // Instructors don't have an academic year.
            binding.tvAcademicYearLabel.visibility = View.GONE
            binding.layoutAcademicYear.visibility = View.GONE
            // Relabel the second document upload to make it obvious to an instructor.
            binding.btnUploadUniversityId.text = "Upload Staff ID"
        }
    }

    private fun showUniversityPicker() {
        val options = UniversityUtil.displayNames()
        val current = binding.etUniversity.text?.toString()?.trim()
        val checked = options.indexOfFirst { it.equals(current, ignoreCase = true) }
        AlertDialog.Builder(this)
            .setTitle("Select University")
            .setSingleChoiceItems(options.toTypedArray(), checked) { dialog, which ->
                binding.etUniversity.setText(options[which])
                binding.etUniversity.error = null
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitVerification() {
        if (!validateForm()) return

        isSubmitting = true
        binding.btnSubmitVerification.isEnabled = false
        binding.btnSubmitVerification.text = "Submitting…"

        val university = binding.etUniversity.text?.toString()?.trim() ?: ""
        val major = binding.etMajor.text?.toString()?.trim() ?: ""
        val verification = StudentVerification(
            uid = uid,
            role = role,
            personalIdNumber = binding.etPersonalId.text?.toString()?.trim() ?: "",
            universityIdNumber = binding.etUniversityId.text?.toString()?.trim() ?: "",
            university = university,
            major = major,
            academicYear = binding.etAcademicYear.text?.toString()?.trim() ?: "",
            personalIdFileName = personalIdFileName,
            universityIdFileName = universityIdFileName,
            personalIdSelected = true,
            universityIdSelected = true,
            status = "PENDING"
        )

        authRepo.saveStudentVerification(
            verification = verification,
            onSuccess = {
                // Also carry university (+key) + department onto the users/{uid} document. This is the
                // field the Find-Instructor filter and the admin-approval workflow rely on. Best-effort:
                // admin still has the verification doc even if this second write fails.
                val userUpdates = mutableMapOf<String, Any?>(
                    "university" to university,
                    "universityKey" to UniversityUtil.keyForDisplayName(university),
                    "verificationSubmitted" to true
                )
                if (role == UserRole.INSTRUCTOR.value) {
                    userUpdates["department"] = major
                } else {
                    userUpdates["major"] = major
                }
                authRepo.updateUserProfile(
                    uid = uid,
                    updates = userUpdates,
                    onSuccess = {
                        // Notify all active admins now that a complete verification exists.
                        authRepo.notifyAdminsOfVerificationSubmitted(uid, fullName, role)
                        navigateToPendingScreen()
                    },
                    onFailure = {
                        authRepo.notifyAdminsOfVerificationSubmitted(uid, fullName, role)
                        navigateToPendingScreen()
                    }
                )
            },
            onFailure = { message ->
                isSubmitting = false
                binding.btnSubmitVerification.isEnabled = true
                binding.btnSubmitVerification.text = "Submit for Review"
                val userMsg = if (message.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "We could not submit your verification because your account does not have permission. " +
                    "Please log out and log in again, or contact the administrator."
                } else {
                    "Submission failed. Please check your connection and try again."
                }
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun validateForm(): Boolean {
        val personalId = binding.etPersonalId.text?.toString()?.trim() ?: ""
        val universityId = binding.etUniversityId.text?.toString()?.trim() ?: ""
        val university = binding.etUniversity.text?.toString()?.trim() ?: ""
        val major = binding.etMajor.text?.toString()?.trim() ?: ""
        val academicYear = binding.etAcademicYear.text?.toString()?.trim() ?: ""

        if (personalId.isEmpty()) {
            binding.etPersonalId.error = "Personal ID number is required"
            binding.etPersonalId.requestFocus()
            return false
        }
        if (!personalId.all { it.isDigit() || it == '-' } || personalId.replace("-", "").length < 5) {
            binding.etPersonalId.error = "Enter a valid ID number (numbers only, at least 5 digits)"
            binding.etPersonalId.requestFocus()
            return false
        }
        if (universityId.isEmpty()) {
            binding.etUniversityId.error = "University ID number is required"
            binding.etUniversityId.requestFocus()
            return false
        }
        if (universityId.length < 5) {
            binding.etUniversityId.error = "University ID must be at least 5 characters"
            binding.etUniversityId.requestFocus()
            return false
        }
        // University must be one of the two allowed values (dropdown-selected).
        if (university.isEmpty() || UniversityUtil.keyForDisplayName(university).isBlank()) {
            binding.etUniversity.error = "Please select your university"
            binding.etUniversity.requestFocus()
            return false
        }
        if (major.isEmpty()) {
            val label = if (role == UserRole.INSTRUCTOR.value) "Department" else "Major / Department"
            binding.etMajor.error = "$label is required"
            binding.etMajor.requestFocus()
            return false
        }
        if (major.length < 2) {
            binding.etMajor.error = "Enter a valid value"
            binding.etMajor.requestFocus()
            return false
        }
        // Academic year is student-only.
        if (role != UserRole.INSTRUCTOR.value) {
            if (academicYear.isEmpty()) {
                binding.etAcademicYear.error = "Academic year is required"
                binding.etAcademicYear.requestFocus()
                return false
            }
            if (!isValidAcademicYear(academicYear)) {
                binding.etAcademicYear.error = "Enter a valid academic year, e.g. 3rd Year or 2024/2025"
                binding.etAcademicYear.requestFocus()
                return false
            }
        }
        if (personalIdFileName == null) {
            Toast.makeText(this, "Please select your Personal ID image", Toast.LENGTH_SHORT).show()
            return false
        }
        if (universityIdFileName == null) {
            Toast.makeText(this, "Please select your University ID card image", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isValidAcademicYear(value: String): Boolean {
        if (value.length < 2) return false
        val lower = value.lowercase()
        return lower.contains("year") ||
            Regex("\\d{4}[/\\-–]\\d{4}").containsMatchIn(value) ||
            Regex("\\d{4}[/\\-–]\\d{2}").containsMatchIn(value) ||
            Regex("^[1-4](st|nd|rd|th)", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    private fun navigateToPendingScreen() {
        val user = User(
            uid = uid,
            email = email,
            fullName = fullName,
            role = role,
            accountStatus = AccountStatus.PENDING.value
        )
        startActivity(
            AccountStatusActivity.newIntent(this, user).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            null
        }
    }
}
