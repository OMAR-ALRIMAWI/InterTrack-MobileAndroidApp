package com.example.intertrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.R
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepo = FirebaseAuthRepository()
    private var isLoggingIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgetPasswordActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            if (isLoggingIn) return@setOnClickListener
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text?.toString()?.trim() ?: ""
        val password = binding.passwordInput.text?.toString() ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter your password.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoggingIn = true
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Signing in…"

        authRepo.login(
            email = email,
            password = password,
            onSuccess = { user ->
                isLoggingIn = false
                routeByUserState(user)
            },
            onFailure = { message ->
                isLoggingIn = false
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.login)
                binding.passwordInput.text?.clear()
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun routeByUserState(user: User) {
        val role = user.resolvedRole()
        if (role == null) {
            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = getString(R.string.login)
            Toast.makeText(this, "Invalid account role. Please contact support.", Toast.LENGTH_LONG).show()
            return
        }

        if (user.isDeleted()) {
            authRepo.logout()
            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = getString(R.string.login)
            Toast.makeText(this, "This account has been deleted. Please contact support.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = when {
            role == UserRole.ADMIN && user.isActive() ->
                Intent(this, AdminDashboardActivity::class.java)

            role != UserRole.ADMIN && user.isActive() ->
                when (role) {
                    UserRole.STUDENT          -> Intent(this, StudentDashBoard::class.java)
                    UserRole.INSTRUCTOR       -> Intent(this, InstructorDashBoard::class.java)
                    UserRole.COMPANY_SUPERVISOR -> Intent(this, CompanyDashBoard::class.java)
                    UserRole.ADMIN            -> return
                }

            user.isPending() || user.isRejected() || user.isBlocked() ->
                AccountStatusActivity.newIntent(this, user)

            else -> {
                authRepo.logout()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.login)
                Toast.makeText(this, "Unexpected account state. Please contact support.", Toast.LENGTH_LONG).show()
                return
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
