package com.example.intertrack.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.R
import com.example.intertrack.databinding.ActivityChooseYourRoleBinding
import com.google.android.material.card.MaterialCardView

class ChooseYourRoleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_ROLE = "SELECTED_ROLE"
    }

    private lateinit var binding: ActivityChooseYourRoleBinding

    private var selectedRole: Role? = null

    private enum class Role { STUDENT, INSTRUCTOR, COMPANY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityChooseYourRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        resetSelection()

        binding.roleStudentCard.setOnClickListener    { selectRole(Role.STUDENT) }
        binding.roleSupervisorCard.setOnClickListener { selectRole(Role.INSTRUCTOR) }
        binding.roleCompanyCard.setOnClickListener    { selectRole(Role.COMPANY) }

        binding.continueButton.setOnClickListener {
            val role = selectedRole
            if (role == null) {
                Toast.makeText(this, "Please choose a role to continue.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fromRegistration = intent.getBooleanExtra("fromRegistration", false)
            if (fromRegistration) {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_ROLE, role.toUserRoleValue())
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                // Reached without going through registration — send back to login
                Toast.makeText(this, "Please log in to continue.", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }

        binding.backToLogin.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
    }

    private fun Role.toUserRoleValue(): String = when (this) {
        Role.STUDENT -> "STUDENT"
        Role.INSTRUCTOR -> "INSTRUCTOR"
        Role.COMPANY -> "COMPANY_SUPERVISOR"
    }

    private fun selectRole(role: Role) {
        selectedRole = role
        resetSelection()

        when (role) {
            Role.STUDENT -> setSelectedCard(
                binding.roleStudentCard, binding.studentIconCard,
                binding.studentIcon, binding.studentTitle, binding.studentDesc)
            Role.INSTRUCTOR -> setSelectedCard(
                binding.roleSupervisorCard, binding.supervisorIconCard,
                binding.instructoricon, binding.supervisorTitle, binding.supervisorDesc)
            Role.COMPANY -> setSelectedCard(
                binding.roleCompanyCard, binding.companyIconCard,
                binding.companysupervisoricon, binding.companyTitle, binding.companyDesc)
        }

        binding.continueButton.isEnabled = true
        binding.continueButton.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#0569bf"))
    }

    private fun resetSelection() {
        setDefaultCard(binding.roleStudentCard, binding.studentIconCard,
            binding.studentIcon, binding.studentTitle, binding.studentDesc)
        setDefaultCard(binding.roleSupervisorCard, binding.supervisorIconCard,
            binding.instructoricon, binding.supervisorTitle, binding.supervisorDesc)
        setDefaultCard(binding.roleCompanyCard, binding.companyIconCard,
            binding.companysupervisoricon, binding.companyTitle, binding.companyDesc)

        if (selectedRole == null) {
            binding.continueButton.isEnabled = false
            binding.continueButton.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
        }
    }

    private fun setSelectedCard(
        card: MaterialCardView, iconCard: MaterialCardView,
        icon: ImageView, title: TextView, desc: TextView
    ) {
        card.setCardBackgroundColor(Color.parseColor("#0569bf"))
        card.strokeWidth = 0
        iconCard.setCardBackgroundColor(Color.WHITE)
        iconCard.strokeWidth = 0
        icon.setColorFilter(Color.parseColor("#0569bf"))
        title.setTextColor(Color.WHITE)
        desc.setTextColor(Color.WHITE)
    }

    private fun setDefaultCard(
        card: MaterialCardView, iconCard: MaterialCardView,
        icon: ImageView, title: TextView, desc: TextView
    ) {
        card.setCardBackgroundColor(Color.WHITE)
        card.strokeColor = Color.parseColor("#E2E8F4")
        card.strokeWidth = 1
        iconCard.setCardBackgroundColor(Color.parseColor("#EAF2FF"))
        iconCard.strokeWidth = 0
        icon.setColorFilter(Color.parseColor("#0569bf"))
        title.setTextColor(Color.parseColor("#111827"))
        desc.setTextColor(Color.parseColor("#5B6478"))
    }
}
