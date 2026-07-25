package com.example.intertrack.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.R
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityAccountStatusBinding

class AccountStatusActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_UID = "extra_uid"
        private const val EXTRA_EMAIL = "extra_email"
        private const val EXTRA_FULL_NAME = "extra_full_name"
        private const val EXTRA_ROLE = "extra_role"
        private const val EXTRA_ACCOUNT_STATUS = "extra_account_status"
        private const val EXTRA_REJECTION_REASON = "extra_rejection_reason"

        fun newIntent(context: Context, user: User): Intent =
            Intent(context, AccountStatusActivity::class.java).apply {
                putExtra(EXTRA_UID, user.uid)
                putExtra(EXTRA_EMAIL, user.email)
                putExtra(EXTRA_FULL_NAME, user.fullName)
                putExtra(EXTRA_ROLE, user.role)
                putExtra(EXTRA_ACCOUNT_STATUS, user.accountStatus)
                putExtra(EXTRA_REJECTION_REASON, user.rejectionReason)
            }
    }

    private lateinit var binding: ActivityAccountStatusBinding
    private val authRepo = FirebaseAuthRepository()

    private lateinit var uid: String
    private lateinit var email: String
    private lateinit var role: String
    private var currentStatus: String = ""
    private var isRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAccountStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix: preserve 20dp horizontal margin while adding system bar vertical insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.accountStatusRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val hPad = (20 * resources.displayMetrics.density).toInt()
            v.setPadding(hPad, systemBars.top, hPad, systemBars.bottom)
            insets
        }

        uid = intent.getStringExtra(EXTRA_UID) ?: ""
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        val fullName = intent.getStringExtra(EXTRA_FULL_NAME) ?: ""
        role = intent.getStringExtra(EXTRA_ROLE) ?: ""
        currentStatus = intent.getStringExtra(EXTRA_ACCOUNT_STATUS) ?: ""
        val rejectionReason = intent.getStringExtra(EXTRA_REJECTION_REASON)

        binding.tvAccountEmail.text = email
        binding.tvAccountRole.text = UserRole.fromString(role)?.displayName() ?: role

        applyStatusUI(currentStatus, rejectionReason)

        binding.btnRefreshStatus.setOnClickListener {
            if (isRefreshing) return@setOnClickListener
            refreshStatus()
        }

        binding.btnStatusLogout.setOnClickListener {
            authRepo.logout()
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private fun refreshStatus() {
        // Blocked users can't refresh — they must contact support
        if (currentStatus == AccountStatus.BLOCKED.value) {
            Toast.makeText(this, "Your account is blocked. Contact support for assistance.", Toast.LENGTH_LONG).show()
            return
        }

        isRefreshing = true
        binding.btnRefreshStatus.isEnabled = false
        binding.btnRefreshStatus.text = "Checking…"

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                isRefreshing = false
                binding.btnRefreshStatus.isEnabled = true
                binding.btnRefreshStatus.text = "Refresh Status"
                currentStatus = user.accountStatus

                when {
                    user.isActive() -> {
                        val resolvedRole = user.resolvedRole() ?: run {
                            authRepo.logout()
                            showToastAndGoToLogin("Invalid account role.")
                            return@fetchUserDocument
                        }
                        routeToDashboard(resolvedRole)
                    }
                    user.isPending() -> {
                        Toast.makeText(this, "Your account is still under review.", Toast.LENGTH_SHORT).show()
                    }
                    user.isRejected() -> {
                        applyStatusUI(AccountStatus.REJECTED.value, user.rejectionReason)
                    }
                    user.isBlocked() -> {
                        applyStatusUI(AccountStatus.BLOCKED.value, null)
                    }
                    else -> {
                        authRepo.logout()
                        showToastAndGoToLogin("Unexpected account state.")
                    }
                }
            },
            onFailure = { message ->
                isRefreshing = false
                binding.btnRefreshStatus.isEnabled = true
                binding.btnRefreshStatus.text = "Refresh Status"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun applyStatusUI(status: String, rejectionReason: String?) {
        when (status) {
            AccountStatus.PENDING.value -> {
                binding.tvStatusBadge.text = "Pending Review"
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pending)
                binding.tvStatusBadge.setTextColor(Color.parseColor("#92400E"))
                binding.tvStatusTitle.text = "Account Under Review"
                binding.tvStatusMessage.text =
                    "Your registration request has been submitted.\n\n" +
                    "An administrator will review your account soon. " +
                    "Tap Refresh Status to check for updates."
                binding.rejectionReasonContainer.visibility = View.GONE
                binding.btnRefreshStatus.visibility = View.VISIBLE
            }
            AccountStatus.REJECTED.value -> {
                binding.tvStatusBadge.text = "Rejected"
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_needs_mod)
                binding.tvStatusBadge.setTextColor(Color.parseColor("#991B1B"))
                binding.tvStatusTitle.text = "Account Rejected"
                binding.tvStatusMessage.text =
                    "Your registration request was not approved. " +
                    "Please contact support if you believe this is a mistake."
                // Always surface the reason section on the rejected screen. When the admin left the
                // dialog blank we show a friendly fallback, but when they typed a real reason we
                // show it verbatim (e.g. admin types "no" → user sees "no").
                binding.rejectionReasonContainer.visibility = View.VISIBLE
                binding.tvRejectionReason.text = rejectionReason?.takeIf { it.isNotBlank() }
                    ?: "Application did not meet verification requirements."
                binding.btnRefreshStatus.visibility = View.VISIBLE
            }
            AccountStatus.BLOCKED.value -> {
                binding.tvStatusBadge.text = "Blocked"
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_blocked)
                binding.tvStatusBadge.setTextColor(Color.parseColor("#FFFFFF"))
                binding.tvStatusTitle.text = "Account Blocked"
                binding.tvStatusMessage.text =
                    "Your account has been suspended by an administrator. " +
                    "Please contact InterTrack support for more information."
                binding.rejectionReasonContainer.visibility = View.GONE
                binding.btnRefreshStatus.visibility = View.GONE
            }
            else -> {
                binding.tvStatusTitle.text = "Account Status Unknown"
                binding.tvStatusMessage.text = "Please contact support."
                binding.btnRefreshStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun routeToDashboard(role: UserRole) {
        val intent = when (role) {
            UserRole.STUDENT          -> Intent(this, StudentDashBoard::class.java)
            UserRole.INSTRUCTOR       -> Intent(this, InstructorDashBoard::class.java)
            UserRole.COMPANY_SUPERVISOR -> Intent(this, CompanyDashBoard::class.java)
            UserRole.ADMIN            -> Intent(this, AdminDashboardActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun showToastAndGoToLogin(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
