package com.example.intertrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val authRepo = FirebaseAuthRepository()
    private var isRegistering = false

    private val rolePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                setRegisteringState(false)
                return@registerForActivityResult
            }
            val roleValue = result.data?.getStringExtra(ChooseYourRoleActivity.EXTRA_SELECTED_ROLE)
            val role = UserRole.fromString(roleValue)
            if (role == null) {
                setRegisteringState(false)
                Toast.makeText(this, "Invalid role selected. Please try again.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val fullName = binding.nameInput.text?.toString()?.trim() ?: ""
            val email = binding.emailInput.text?.toString()?.trim() ?: ""
            val password = binding.passwordInput.text?.toString() ?: ""

            authRepo.register(
                email = email,
                password = password,
                fullName = fullName,
                role = role,
                onSuccess = { user ->
                    isRegistering = false
                    routeAfterRegistration(user)
                },
                onFailure = { message ->
                    setRegisteringState(false)
                    binding.passwordInput.text?.clear()
                    binding.confirmPasswordInput.text?.clear()
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.loginAction.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.registerButton.setOnClickListener {
            if (isRegistering) return@setOnClickListener
            attemptRegister()
        }
    }

    private fun attemptRegister() {
        val fullName = binding.nameInput.text?.toString()?.trim() ?: ""
        val email = binding.emailInput.text?.toString()?.trim() ?: ""
        val password = binding.passwordInput.text?.toString() ?: ""
        val confirmPassword = binding.confirmPasswordInput.text?.toString() ?: ""

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name.", Toast.LENGTH_SHORT).show()
            return
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            binding.confirmPasswordInput.text?.clear()
            return
        }

        setRegisteringState(true)
        val intent = Intent(this, ChooseYourRoleActivity::class.java).apply {
            putExtra("fromRegistration", true)
        }
        rolePickerLauncher.launch(intent)
    }

    private fun setRegisteringState(registering: Boolean) {
        isRegistering = registering
        binding.registerButton.isEnabled = !registering
        binding.registerButton.text = if (registering) "Creating account…" else getString(
            com.example.intertrack.R.string.register
        )
    }

    private fun routeAfterRegistration(user: User) {
        // Every non-admin role must submit verification before reaching the Account Under Review
        // screen — this guarantees the Admin's Pending Requests always has real proof/details to
        // review, and prevents company supervisors from bypassing verification.
        val intent = when (UserRole.fromString(user.role)) {
            UserRole.STUDENT, UserRole.INSTRUCTOR -> StudentVerificationActivity.newIntent(this, user)
            UserRole.COMPANY_SUPERVISOR -> CompanyVerificationActivity.newIntent(this, user)
            else -> AccountStatusActivity.newIntent(this, user)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
