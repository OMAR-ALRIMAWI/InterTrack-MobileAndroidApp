package com.example.intertrack.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.R
import com.example.intertrack.databinding.ActivityForgetPasswordBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class ForgetPasswordActivity : AppCompatActivity() {
    lateinit var binding: ActivityForgetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityForgetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.e("FORGOT_PASSWORD", "ForgetPasswordActivity opened")
        
        val projectId = try {
            FirebaseApp.getInstance().options.projectId
        } catch (e: Exception) {
            "Unknown"
        }
        Log.e("FORGOT_PASSWORD", "Firebase projectId: $projectId")
        
        binding.tvDebugTag.text = "Debug tag: FORGOT_PASSWORD | Project: $projectId"

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.sendResetButton.setOnClickListener {
            Log.e("FORGOT_PASSWORD", "Reset button clicked")
            val email = binding.emailInput.text?.toString()?.trim() ?: ""
            Log.e("FORGOT_PASSWORD", "Final email used: '$email'")
            
            when {
                email.isEmpty() -> {
                    binding.emailInput.error = "Enter your email first"
                    binding.tvResetStatus.text = "Enter your email first"
                    binding.tvResetStatus.setTextColor(android.graphics.Color.RED)
                    binding.emailInput.requestFocus()
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    binding.emailInput.error = "Enter a valid email"
                    binding.tvResetStatus.text = "Enter a valid email"
                    binding.tvResetStatus.setTextColor(android.graphics.Color.RED)
                    binding.emailInput.requestFocus()
                }
                else -> {
                    binding.sendResetButton.isEnabled = false
                    binding.sendResetButton.text = "Sending…"
                    binding.tvResetStatus.text = "Sending reset email..."
                    binding.tvResetStatus.setTextColor(android.graphics.Color.BLUE)
                    
                    Log.e("FORGOT_PASSWORD", "Sending reset to: $email")
                    
                    FirebaseAuth.getInstance()
                        .sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Log.e("FORGOT_PASSWORD", "Firebase reset request SUCCESS for: $email")
                            binding.sendResetButton.isEnabled = true
                            binding.sendResetButton.text = "Send Reset Link"
                            
                            val successMsg = "Firebase accepted the reset request. Check inbox, spam, promotions, and make sure this email exists in the same Firebase project."
                            binding.tvResetStatus.text = "Success"
                            binding.tvResetStatus.setTextColor(android.graphics.Color.parseColor("#16A34A")) // Green
                            
                            showDiagnostics(projectId, email)
                            
                            Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            val errorCode = (e as? FirebaseAuthException)?.errorCode
                            Log.e("FORGOT_PASSWORD", "Reset email failed for: $email", e)
                            if (errorCode != null) {
                                Log.e("FORGOT_PASSWORD", "Firebase error code: $errorCode")
                            }
                            Log.e("FORGOT_PASSWORD", "Exception type: ${e.javaClass.simpleName}")
                            
                            binding.sendResetButton.isEnabled = true
                            binding.sendResetButton.text = "Send Reset Link"
                            
                            val errorMsg = if (errorCode != null) {
                                "Reset failed: $errorCode - ${e.message}"
                            } else {
                                "Reset failed: ${e.message}"
                            }
                            
                            binding.tvResetStatus.text = errorMsg
                            binding.tvResetStatus.setTextColor(android.graphics.Color.RED)
                            
                            showDiagnostics(projectId, email)
                            
                            Toast.makeText(this, e.message ?: "Failed to send reset email.", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }

        binding.backToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    
    private fun showDiagnostics(projectId: String?, email: String) {
        binding.diagnosticCard.visibility = View.VISIBLE
        binding.tvDiagnosticInfo.text = "Project: $projectId\nEmail used: $email"
    }
}