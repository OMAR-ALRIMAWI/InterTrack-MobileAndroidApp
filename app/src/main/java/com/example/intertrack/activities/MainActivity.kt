package com.example.intertrack.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.intertrack.R
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private val authRepo = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startFloatingAnimation(binding.iconDocument, 18f, 4200)
        startFloatingAnimation(binding.graduateHatIcon, 22f, 5000)
        startFloatingAnimation(binding.laptopIcon, 16f, 4600)
        startFloatingAnimation(binding.phoneIcon, 20f, 5400)

        val currentUser = authRepo.currentFirebaseUser()
        if (currentUser != null) {
            binding.beginJourneyButton.isEnabled = false
            binding.beginJourneyButton.text = "Loading…"
            authRepo.fetchUserDocument(
                uid = currentUser.uid,
                onSuccess = { user -> routeByUserState(user) },
                onFailure = {
                    authRepo.logout()
                    restoreNormalSplash()
                }
            )
        } else {
            binding.beginJourneyButton.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }

    private fun restoreNormalSplash() {
        binding.beginJourneyButton.isEnabled = true
        binding.beginJourneyButton.text = getString(R.string.begin_your_journey)
        binding.beginJourneyButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun routeByUserState(user: User) {
        val role = user.resolvedRole() ?: run {
            authRepo.logout()
            restoreNormalSplash()
            return
        }

        if (user.isDeleted()) {
            authRepo.logout()
            restoreNormalSplash()
            return
        }

        val intent: Intent = when {
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
                restoreNormalSplash()
                return
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun startFloatingAnimation(view: ImageView, distance: Float, duration: Long) {
        val moveY = ObjectAnimator.ofFloat(view, "translationY", 0f, distance, 0f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val rotate = ObjectAnimator.ofFloat(
            view, "rotation", view.rotation, view.rotation + 4f, view.rotation
        ).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(moveY, rotate)
            start()
        }
    }
}
