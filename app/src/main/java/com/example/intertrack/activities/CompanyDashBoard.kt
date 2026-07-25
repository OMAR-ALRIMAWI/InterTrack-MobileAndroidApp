package com.example.intertrack.activities

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.intertrack.R
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.ActivityCompanyDashBoardBinding
import com.example.intertrack.fragments.CompanyApplicationsFragment
import com.example.intertrack.fragments.CompanyHomeFragment
import com.example.intertrack.fragments.CompanyMessagesFragment
import com.example.intertrack.fragments.CompanySupervisorProfileFragment
import com.example.intertrack.fragments.CompanySupervisorReviewFragment
import com.example.intertrack.fragments.NotificationFragment
import com.google.firebase.auth.FirebaseAuth
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

class CompanyDashBoard : AppCompatActivity() {

    private lateinit var binding: ActivityCompanyDashBoardBinding
    private val authRepo = FirebaseAuthRepository()

    private val activeColor       = Color.parseColor("#0569bf")
    private val inactiveColor     = Color.parseColor("#B8B2C4")
    private val inactiveTextColor = Color.parseColor("#9CA3AF")

    private var notificationPopup: PopupWindow? = null
    var hasUnreadNotifications = true
    private var currentTabIndex = 0
    private lateinit var swipeDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanyDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.navigationBarColor = Color.parseColor("#F6F9FC")

        if (savedInstanceState == null) {
            replaceFragment(CompanyHomeFragment())
            selectNavItem("home")
            val displayName = AppSessionCache.currentCompany?.name
                ?: AppSessionCache.currentUser?.fullName?.split(" ")?.first()
                ?: "Supervisor"
            updateHeader("Dashboard", "Hello, $displayName")
        }

        updateUnreadBadge()
        addNavigationListeners()
        setupSwipeDetector()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (notificationPopup?.isShowing == true) {
                    dismissNotificationPopup()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        checkUserStillActive()
    }

    private fun checkUserStillActive() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { forceLogout(); return }
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user -> if (user.isDeleted() || user.isBlocked()) forceLogout() },
            onFailure = {}
        )
    }

    private fun forceLogout() {
        authRepo.logout()
        val intent = android.content.Intent(this, LoginActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    // ── Public API for fragments ──────────────────────────────────────────────

    fun setHeaderVisible(visible: Boolean) {
        binding.compSharedHeader.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            binding.compFragmentContainer.setBackgroundResource(R.drawable.bg_body_top_rounded)
        } else {
            binding.compFragmentContainer.background = null
        }
    }

    fun setNavVisible(visible: Boolean) {
        binding.compBottomNavContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun updateHeader(title: String, subtitle: String? = null) {
        binding.compTvPageTitle.text = title
        if (subtitle != null) {
            binding.compTvPageSubtitle.text = subtitle
            binding.compTvPageSubtitle.visibility = View.VISIBLE
        } else {
            binding.compTvPageSubtitle.visibility = View.GONE
        }
    }

    fun navigateTo(tab: String) {
        when (tab) {
            "home"         -> {
                currentTabIndex = 0
                replaceFragment(CompanyHomeFragment())
                selectNavItem("home")
                val displayName = AppSessionCache.currentCompany?.name
                    ?: AppSessionCache.currentUser?.fullName?.split(" ")?.first()
                    ?: "Supervisor"
                updateHeader("Dashboard", "Hello, $displayName")
            }
            // Applications is no longer a bottom-nav tab — it opens as a detail screen from
            // Review → "View Student Applications". Back returns to Review.
            "applications" -> { openDetail(CompanyApplicationsFragment()); updateHeader("Applications") }
            "review"       -> { currentTabIndex = 1; replaceFragment(CompanySupervisorReviewFragment()); selectNavItem("review");       updateHeader("Review") }
            "messages"     -> { currentTabIndex = 2; replaceFragment(CompanyMessagesFragment());         selectNavItem("messages");     updateHeader("Messages") }
            "profile"      -> { currentTabIndex = 3; replaceFragment(CompanySupervisorProfileFragment());selectNavItem("profile");     updateHeader("Profile") }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    private fun addNavigationListeners() {
        binding.compNavHome.setOnClickListener         { navigateTo("home") }
        binding.compNavReview.setOnClickListener       { navigateTo("review") }
        binding.compNavMessages.setOnClickListener     { navigateTo("messages") }
        binding.compNavProfile.setOnClickListener      { navigateTo("profile") }

        binding.compNotificationWrap.setOnClickListener { v ->
            animateButtonPress(v)
            if (notificationPopup?.isShowing == true) dismissNotificationPopup()
            else showNotificationPopup()
        }
    }

    // ── Notification popup ────────────────────────────────────────────────────

    private fun showNotificationPopup() {
        val popupView = layoutInflater.inflate(R.layout.layout_notification_popup, null)
        val density   = resources.displayMetrics.density
        val popupWidth = (320 * density).toInt()

        val popup = PopupWindow(popupView, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 20f * density

        val rvPopup   = popupView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPopupNotifications)
        val progress  = popupView.findViewById<View>(R.id.popupNotifProgress)
        val emptyView = popupView.findViewById<TextView>(R.id.tvPopupNotifEmpty)
        rvPopup.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            authRepo.getNotifications(
                recipientUid = uid,
                onSuccess = { all ->
                    val items = all.take(5)
                    progress.visibility = View.GONE
                    if (items.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        rvPopup.visibility = View.VISIBLE
                        rvPopup.adapter = com.example.intertrack.fragments.PopupNotificationAdapter(items) {
                            popup.dismiss()
                            replaceFragment(NotificationFragment())
                            clearNavSelection()
                            updateHeader("Notifications")
                        }
                        popup.update()
                    }
                },
                onFailure = {
                    progress.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                }
            )
        } else {
            progress.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        }

        popupView.findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            hasUnreadNotifications = false
            updateUnreadBadge()
        }

        popupView.findViewById<TextView>(R.id.tvSeeAllNotifications).setOnClickListener {
            popup.dismiss()
            replaceFragment(NotificationFragment())
            clearNavSelection()
            updateHeader("Notifications")
        }

        popupView.pivotX = popupWidth.toFloat(); popupView.pivotY = 0f
        popupView.scaleX = 0.92f; popupView.scaleY = 0.92f; popupView.alpha = 0f

        popup.showAsDropDown(
            binding.compNotificationWrap,
            binding.compNotificationWrap.width - popupWidth,
            (8 * density).toInt()
        )

        popupView.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).start()

        popup.setOnDismissListener { notificationPopup = null }
        notificationPopup = popup
    }

    private fun dismissNotificationPopup() {
        val popup = notificationPopup ?: return
        popup.contentView.animate()
            .scaleX(0.92f).scaleY(0.92f).alpha(0f)
            .setDuration(150).setInterpolator(DecelerateInterpolator())
            .withEndAction { popup.dismiss() }.start()
    }

    private var bellPulse: android.animation.Animator? = null

    fun updateUnreadBadge() {
        binding.compNotifUnreadDot.visibility =
            if (hasUnreadNotifications) View.VISIBLE else View.GONE
        if (hasUnreadNotifications) startBellPulse() else stopBellPulse()
    }

    private fun startBellPulse() {
        if (bellPulse != null) return
        val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.compNotificationIcon,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(View.ROTATION, 0f, -8f, 8f, 0f)
        ).apply {
            duration = 900
            repeatCount = android.animation.ValueAnimator.INFINITE
            startDelay = 400
        }
        bellPulse = pulse
        pulse.start()
    }

    private fun stopBellPulse() {
        bellPulse?.cancel()
        bellPulse = null
        binding.compNotificationIcon.apply { scaleX = 1f; scaleY = 1f; rotation = 0f }
    }

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }.start()
    }

    // ── Nav selection helpers ─────────────────────────────────────────────────

    private fun selectNavItem(selected: String) {
        clearNavSelection()
        when (selected) {
            "home"         -> setActive(binding.compHomeIconHolder,         binding.compHomeIcon,         binding.compHomeText)
            "review"       -> setActive(binding.compReviewIconHolder,       binding.compReviewIcon,       binding.compReviewText)
            "messages"     -> setActive(binding.compMessagesIconHolder,     binding.compMessagesIcon,     binding.compMessagesText)
            "profile"      -> setActive(binding.compProfileIconHolder,      binding.compProfileIcon,      binding.compProfileText)
        }
    }

    private fun clearNavSelection() {
        setInactive(binding.compHomeIconHolder,         binding.compHomeIcon,         binding.compHomeText)
        setInactive(binding.compReviewIconHolder,       binding.compReviewIcon,       binding.compReviewText)
        setInactive(binding.compMessagesIconHolder,     binding.compMessagesIcon,     binding.compMessagesText)
        setInactive(binding.compProfileIconHolder,      binding.compProfileIcon,      binding.compProfileText)
    }

    private fun setActive(holder: FrameLayout, icon: ImageView, text: TextView) {
        holder.background = ContextCompat.getDrawable(this, R.drawable.bg_nav_selected_pill)
        icon.setColorFilter(Color.WHITE)
        text.setTextColor(activeColor)
        text.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun setInactive(holder: FrameLayout, icon: ImageView, text: TextView) {
        holder.background = null
        icon.setColorFilter(inactiveColor)
        text.setTextColor(inactiveTextColor)
        text.setTypeface(null, android.graphics.Typeface.NORMAL)
    }

    fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(binding.compFragmentContainer.id, fragment)
            .commit()
    }

    fun openDetail(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                 R.anim.slide_in_left,  R.anim.slide_out_right)
            .replace(binding.compFragmentContainer.id, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun replaceFragmentSwipe(fragment: androidx.fragment.app.Fragment, forward: Boolean) {
        val enter = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        val exit  = if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(enter, exit)
            .replace(binding.compFragmentContainer.id, fragment)
            .commit()
    }

    private fun navigateToTabIndex(index: Int) {
        val clamped = index.coerceIn(0, 3)
        if (clamped == currentTabIndex) return
        val forward = clamped > currentTabIndex
        currentTabIndex = clamped
        setHeaderVisible(true)
        setNavVisible(true)
        val displayName = AppSessionCache.currentCompany?.name
            ?: AppSessionCache.currentUser?.fullName?.split(" ")?.first() ?: "Supervisor"
        when (currentTabIndex) {
            0 -> { replaceFragmentSwipe(CompanyHomeFragment(), forward);             selectNavItem("home");     updateHeader("Dashboard", "Hello, $displayName") }
            1 -> { replaceFragmentSwipe(CompanySupervisorReviewFragment(), forward); selectNavItem("review");   updateHeader("Review") }
            2 -> { replaceFragmentSwipe(CompanyMessagesFragment(), forward);         selectNavItem("messages"); updateHeader("Messages") }
            3 -> { replaceFragmentSwipe(CompanySupervisorProfileFragment(), forward);selectNavItem("profile");  updateHeader("Profile") }
        }
    }

    private fun setupSwipeDetector() {
        swipeDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                if (kotlin.math.abs(dx) < kotlin.math.abs(dy) * 2.5f) return false
                if (kotlin.math.abs(vx) < 400f) return false
                if (supportFragmentManager.backStackEntryCount > 0) return false
                if (dx < 0) navigateToTabIndex(currentTabIndex + 1)
                else navigateToTabIndex(currentTabIndex - 1)
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::swipeDetector.isInitialized) swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
