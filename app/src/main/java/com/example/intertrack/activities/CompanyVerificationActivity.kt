package com.example.intertrack.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.CompanyVerification
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityCompanyVerificationBinding

/**
 * Company Supervisor's verification form, shown right after registration and before the account
 * reaches Admin Pending Requests. On submit the form saves a `verifications/{uid}` doc (metadata-only
 * proof), mirrors safe fields into `users/{uid}`, and notifies every active admin.
 */
class CompanyVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UID = "cvf_uid"
        const val EXTRA_EMAIL = "cvf_email"
        const val EXTRA_FULL_NAME = "cvf_full_name"

        fun newIntent(context: Context, user: User): Intent =
            Intent(context, CompanyVerificationActivity::class.java).apply {
                putExtra(EXTRA_UID, user.uid)
                putExtra(EXTRA_EMAIL, user.email)
                putExtra(EXTRA_FULL_NAME, user.fullName)
            }
    }

    private lateinit var binding: ActivityCompanyVerificationBinding
    private val authRepo = FirebaseAuthRepository()

    private lateinit var uid: String
    private lateinit var email: String
    private lateinit var fullName: String

    private var commercialRegistrationFileName: String? = null
    private var commercialRegistrationMimeType: String = ""
    private var authorizationFileName: String? = null
    private var authorizationMimeType: String = ""
    private var isSubmitting = false

    private val commercialRegistrationPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            commercialRegistrationFileName = getFileName(uri) ?: "document_selected"
            commercialRegistrationMimeType = contentResolver.getType(uri) ?: ""
            binding.tvCompRegistrationFileName.text = commercialRegistrationFileName
            binding.btnCompUploadRegistration.text = "Change"
        }
    }

    private val authorizationPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            authorizationFileName = getFileName(uri) ?: "document_selected"
            authorizationMimeType = contentResolver.getType(uri) ?: ""
            binding.tvCompAuthorizationFileName.text = authorizationFileName
            binding.btnCompUploadAuthorization.text = "Change"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanyVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = intent.getStringExtra(EXTRA_UID) ?: ""
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        fullName = intent.getStringExtra(EXTRA_FULL_NAME) ?: ""

        // Pre-fill supervisor name from registration + hint the company email with the sign-up email.
        binding.etCompSupervisorName.setText(fullName)
        binding.etCompCompanyEmail.setText(email)

        binding.btnCompUploadRegistration.setOnClickListener {
            commercialRegistrationPicker.launch("*/*")
        }
        binding.btnCompUploadAuthorization.setOnClickListener {
            authorizationPicker.launch("*/*")
        }
        binding.btnCompSubmitVerification.setOnClickListener {
            if (!isSubmitting) submitVerification()
        }
    }

    private fun submitVerification() {
        if (!validateForm()) return

        isSubmitting = true
        binding.btnCompSubmitVerification.isEnabled = false
        binding.btnCompSubmitVerification.text = "Submitting…"

        val companyName = binding.etCompCompanyName.text?.toString()?.trim() ?: ""
        val companyEmail = binding.etCompCompanyEmail.text?.toString()?.trim() ?: ""
        val registrationNumber = binding.etCompRegNumber.text?.toString()?.trim() ?: ""
        val address = binding.etCompAddress.text?.toString()?.trim() ?: ""
        val phone = binding.etCompPhone.text?.toString()?.trim() ?: ""
        val website = binding.etCompWebsite.text?.toString()?.trim() ?: ""
        val supervisorName = binding.etCompSupervisorName.text?.toString()?.trim() ?: ""
        val position = binding.etCompSupervisorPosition.text?.toString()?.trim() ?: ""

        val verification = CompanyVerification(
            uid = uid,
            companyName = companyName,
            companyEmail = companyEmail,
            companyRegistrationNumber = registrationNumber,
            companyAddress = address,
            companyPhone = phone,
            companyWebsite = website,
            supervisorFullName = supervisorName,
            supervisorPosition = position,
            commercialRegistrationFileName = commercialRegistrationFileName,
            commercialRegistrationMimeType = commercialRegistrationMimeType,
            authorizationFileName = authorizationFileName,
            authorizationMimeType = authorizationMimeType,
            proofUrl = null,
            status = "PENDING"
        )

        authRepo.saveCompanyVerification(
            verification = verification,
            onSuccess = {
                // Mirror safe fields onto users/{uid} so admin lists and later flows have them.
                val userUpdates = mapOf<String, Any?>(
                    "companyName" to companyName,
                    "companyEmail" to companyEmail,
                    "verificationSubmitted" to true
                )
                authRepo.updateUserProfile(
                    uid = uid,
                    updates = userUpdates,
                    onSuccess = {
                        authRepo.notifyAdminsOfVerificationSubmitted(
                            uid, supervisorName.ifBlank { fullName }, UserRole.COMPANY_SUPERVISOR.value
                        )
                        navigateToPendingScreen()
                    },
                    onFailure = {
                        authRepo.notifyAdminsOfVerificationSubmitted(
                            uid, supervisorName.ifBlank { fullName }, UserRole.COMPANY_SUPERVISOR.value
                        )
                        navigateToPendingScreen()
                    }
                )
            },
            onFailure = { message ->
                isSubmitting = false
                binding.btnCompSubmitVerification.isEnabled = true
                binding.btnCompSubmitVerification.text = "Submit for Review"
                val userMsg = if (message.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "We could not submit your verification because your account does not have permission. " +
                        "Please log out and sign in again."
                } else {
                    "Submission failed. Please check your connection and try again."
                }
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun validateForm(): Boolean {
        val companyName = binding.etCompCompanyName.text?.toString()?.trim() ?: ""
        val companyEmail = binding.etCompCompanyEmail.text?.toString()?.trim() ?: ""
        val regNumber = binding.etCompRegNumber.text?.toString()?.trim() ?: ""
        val address = binding.etCompAddress.text?.toString()?.trim() ?: ""
        val phone = binding.etCompPhone.text?.toString()?.trim() ?: ""
        val supervisorName = binding.etCompSupervisorName.text?.toString()?.trim() ?: ""
        val position = binding.etCompSupervisorPosition.text?.toString()?.trim() ?: ""

        if (companyName.length < 2) {
            binding.etCompCompanyName.error = "Company name is required"
            binding.etCompCompanyName.requestFocus(); return false
        }
        if (companyEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(companyEmail).matches()) {
            binding.etCompCompanyEmail.error = "Enter a valid company email"
            binding.etCompCompanyEmail.requestFocus(); return false
        }
        if (regNumber.length < 3) {
            binding.etCompRegNumber.error = "Commercial registration number is required"
            binding.etCompRegNumber.requestFocus(); return false
        }
        if (address.length < 4) {
            binding.etCompAddress.error = "Company address is required"
            binding.etCompAddress.requestFocus(); return false
        }
        val phoneDigits = phone.filter { it.isDigit() }
        if (phoneDigits.length < 7 || phoneDigits.length > 15) {
            binding.etCompPhone.error = "Enter a valid phone number"
            binding.etCompPhone.requestFocus(); return false
        }
        if (supervisorName.length < 2) {
            binding.etCompSupervisorName.error = "Supervisor name is required"
            binding.etCompSupervisorName.requestFocus(); return false
        }
        if (position.length < 2) {
            binding.etCompSupervisorPosition.error = "Supervisor position is required"
            binding.etCompSupervisorPosition.requestFocus(); return false
        }
        if (commercialRegistrationFileName.isNullOrBlank()) {
            Toast.makeText(this, "Please attach the Commercial Registration document.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun navigateToPendingScreen() {
        val user = User(
            uid = uid,
            email = email,
            fullName = fullName,
            role = UserRole.COMPANY_SUPERVISOR.value,
            accountStatus = AccountStatus.PENDING.value
        )
        startActivity(
            AccountStatusActivity.newIntent(this, user).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun getFileName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    } catch (e: Exception) { null }
}
