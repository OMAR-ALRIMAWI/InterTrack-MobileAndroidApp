package com.example.intertrack.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.ActivityAdminDashboardBinding
import com.example.intertrack.fragments.NotificationFragment
import com.example.intertrack.fragments.admin.AdminAccountsFragment
import com.example.intertrack.fragments.admin.AdminHomeFragment
import com.example.intertrack.fragments.admin.AdminProfileFragment
import com.example.intertrack.fragments.admin.AdminRequestsFragment
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    val authRepo = FirebaseAuthRepository()

    private val activeColor = Color.parseColor("#0569bf")
    private val inactiveColor = Color.parseColor("#B8B2C4")
    private val inactiveTextColor = Color.parseColor("#9CA3AF")

    var hasUnreadNotifications = false
    private var bellPulse: android.animation.Animator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.navigationBarColor = Color.parseColor("#F6F9FC")

        val adminUser = FirebaseAuth.getInstance().currentUser
        binding.tvAdminEmail.text = adminUser?.email ?: "Administrator"

        if (savedInstanceState == null) {
            replaceFragment(AdminHomeFragment())
            selectNavItem("home")
            updateHeader("Dashboard")
        }

        binding.notificationWrap.setOnClickListener {
            clearBackStack()
            clearNavSelection()
            replaceFragment(NotificationFragment())
            updateHeader("Notifications")
        }

        updateUnreadBadge()
        refreshUnreadBadgeFromFirestore()
        addNavigationListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUnreadBadgeFromFirestore()
    }

    fun updateHeader(title: String) {
        binding.tvPageTitle.text = title
    }

    // ── Unread badge + subtle bell pulse ──────────────────────────────────────

    fun updateUnreadBadge() {
        binding.notifUnreadDot.visibility =
            if (hasUnreadNotifications) android.view.View.VISIBLE else android.view.View.GONE
        if (hasUnreadNotifications) startBellPulse() else stopBellPulse()
    }

    private fun refreshUnreadBadgeFromFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.hasUnreadNotifications(uid) { hasUnread ->
            hasUnreadNotifications = hasUnread
            updateUnreadBadge()
        }
    }

    private fun startBellPulse() {
        if (bellPulse != null) return
        val icon = binding.notificationIcon
        val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            icon,
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.18f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.18f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.ROTATION, 0f, -8f, 8f, 0f)
        ).apply {
            duration = 900
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            startDelay = 400
        }
        bellPulse = pulse
        pulse.start()
    }

    private fun stopBellPulse() {
        bellPulse?.cancel()
        bellPulse = null
        binding.notificationIcon.apply { scaleX = 1f; scaleY = 1f; rotation = 0f }
    }

    fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(binding.adminFragmentContainer.id, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    fun selectNavItem(selected: String) {
        clearNavSelection()
        when (selected) {
            "home"     -> setActive(binding.homeIconHolder, binding.homeIcon, binding.homeText)
            "requests" -> setActive(binding.requestsIconHolder, binding.requestsIcon, binding.requestsText)
            "accounts" -> setActive(binding.accountsIconHolder, binding.accountsIcon, binding.accountsText)
            "profile"  -> setActive(binding.profileIconHolder, binding.profileIcon, binding.profileText)
        }
    }

    fun logout() {
        AppSessionCache.clear()
        authRepo.logout()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun addNavigationListeners() {
        binding.navHome.setOnClickListener {
            clearBackStack()
            replaceFragment(AdminHomeFragment())
            selectNavItem("home")
            updateHeader("Dashboard")
        }
        binding.navRequests.setOnClickListener {
            clearBackStack()
            replaceFragment(AdminRequestsFragment())
            selectNavItem("requests")
            updateHeader("Verification Requests")
        }
        binding.navAccounts.setOnClickListener {
            clearBackStack()
            replaceFragment(AdminAccountsFragment())
            selectNavItem("accounts")
            updateHeader("Manage Accounts")
        }
        binding.navProfile.setOnClickListener {
            clearBackStack()
            replaceFragment(AdminProfileFragment())
            selectNavItem("profile")
            updateHeader("Profile")
        }
    }

    private fun clearBackStack() {
        repeat(supportFragmentManager.backStackEntryCount) {
            supportFragmentManager.popBackStackImmediate()
        }
    }

    private fun clearNavSelection() {
        setInactive(binding.homeIconHolder, binding.homeIcon, binding.homeText)
        setInactive(binding.requestsIconHolder, binding.requestsIcon, binding.requestsText)
        setInactive(binding.accountsIconHolder, binding.accountsIcon, binding.accountsText)
        setInactive(binding.profileIconHolder, binding.profileIcon, binding.profileText)
    }

    private fun setActive(holder: FrameLayout, icon: ImageView, text: TextView) {
        holder.background = ContextCompat.getDrawable(this, R.drawable.bg_nav_selected_pill)
        icon.setColorFilter(Color.WHITE)
        text.setTextColor(activeColor)
        text.setTypeface(null, Typeface.BOLD)
    }

    private fun setInactive(holder: FrameLayout, icon: ImageView, text: TextView) {
        holder.background = null
        icon.setColorFilter(inactiveColor)
        text.setTextColor(inactiveTextColor)
        text.setTypeface(null, Typeface.NORMAL)
    }
}
