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
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.ActivityInstructorDashBoardBinding
import com.example.intertrack.fragments.InstructorHomeFragment
import com.example.intertrack.fragments.InstructorMessagesFragment
import com.example.intertrack.fragments.InstructorProfileFragment
import com.example.intertrack.fragments.InstructorRequestsFragment
import com.example.intertrack.fragments.InstructorReviewFragment
import com.example.intertrack.fragments.NotificationFragment
import com.google.firebase.auth.FirebaseAuth
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

class InstructorDashBoard : AppCompatActivity() {

    private lateinit var binding: ActivityInstructorDashBoardBinding
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
        binding = ActivityInstructorDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.navigationBarColor = Color.parseColor("#F6F9FC")

        if (savedInstanceState == null) {
            replaceFragment(InstructorHomeFragment())
            selectNavItem("students")
            updateHeader("Dashboard", "Hello, Dr. Smith")
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
        binding.instSharedHeader.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            binding.instFragmentContainer.setBackgroundResource(R.drawable.bg_body_top_rounded)
        } else {
            binding.instFragmentContainer.background = null
        }
    }

    fun setNavVisible(visible: Boolean) {
        binding.instBottomNavContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun updateHeader(title: String, subtitle: String? = null) {
        binding.instTvPageTitle.text = title
        if (subtitle != null) {
            binding.instTvPageSubtitle.text = subtitle
            binding.instTvPageSubtitle.visibility = View.VISIBLE
        } else {
            binding.instTvPageSubtitle.visibility = View.GONE
        }
    }

    fun navigateTo(tab: String) {
        when (tab) {
            "students" -> { currentTabIndex = 0; replaceFragment(InstructorHomeFragment());     selectNavItem("students"); updateHeader("Dashboard", "Hello, Dr. Smith") }
            "review"   -> { currentTabIndex = 1; replaceFragment(InstructorReviewFragment());   selectNavItem("review");   updateHeader("Internships") }
            "requests" -> { currentTabIndex = 2; replaceFragment(InstructorRequestsFragment()); selectNavItem("requests"); updateHeader("Requests") }
            "messages" -> { currentTabIndex = 3; replaceFragment(InstructorMessagesFragment()); selectNavItem("messages"); updateHeader("Messages") }
            "profile"  -> { currentTabIndex = 4; replaceFragment(InstructorProfileFragment());  selectNavItem("profile");  updateHeader("Profile") }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    private fun addNavigationListeners() {
        binding.instNavStudents.setOnClickListener { navigateTo("students") }
        binding.instNavReview.setOnClickListener   { navigateTo("review") }
        binding.instNavRequests.setOnClickListener { navigateTo("requests") }
        binding.instNavMessages.setOnClickListener { navigateTo("messages") }
        binding.instNavProfile.setOnClickListener  { navigateTo("profile") }

        binding.instNotificationWrap.setOnClickListener { v ->
            animateButtonPress(v)
            if (notificationPopup?.isShowing == true) {
                dismissNotificationPopup()
            } else {
                showNotificationPopup()
            }
        }
    }

    // ── Notification popup ────────────────────────────────────────────────────

    private fun showNotificationPopup() {
        val popupView = layoutInflater.inflate(R.layout.layout_notification_popup, null)

        val density    = resources.displayMetrics.density
        val popupWidth = (320 * density).toInt()

        val popup = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
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

        popupView.pivotX = popupWidth.toFloat()
        popupView.pivotY = 0f
        popupView.scaleX = 0.92f
        popupView.scaleY = 0.92f
        popupView.alpha  = 0f

        popup.showAsDropDown(
            binding.instNotificationWrap,
            binding.instNotificationWrap.width - popupWidth,
            (8 * density).toInt()
        )

        popupView.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        popup.setOnDismissListener { notificationPopup = null }
        notificationPopup = popup
    }

    private fun dismissNotificationPopup() {
        val popup = notificationPopup ?: return
        val view  = popup.contentView
        view.animate()
            .scaleX(0.92f).scaleY(0.92f)
            .alpha(0f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { popup.dismiss() }
            .start()
    }

    private var bellPulse: android.animation.Animator? = null

    fun updateUnreadBadge() {
        binding.instNotifUnreadDot.visibility =
            if (hasUnreadNotifications) View.VISIBLE else View.GONE
        if (hasUnreadNotifications) startBellPulse() else stopBellPulse()
    }

    private fun startBellPulse() {
        if (bellPulse != null) return
        val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.instNotificationIcon,
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
        binding.instNotificationIcon.apply { scaleX = 1f; scaleY = 1f; rotation = 0f }
    }

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

    // ── Nav selection helpers ─────────────────────────────────────────────────

    private fun selectNavItem(selected: String) {
        clearNavSelection()
        when (selected) {
            "students" -> setActive(binding.instStudentsIconHolder, binding.instStudentsIcon, binding.instStudentsText)
            "review"   -> setActive(binding.instReviewIconHolder,   binding.instReviewIcon,   binding.instReviewText)
            "requests" -> setActive(binding.instRequestsIconHolder, binding.instRequestsIcon, binding.instRequestsText)
            "messages" -> setActive(binding.instMessagesIconHolder, binding.instMessagesIcon, binding.instMessagesText)
            "profile"  -> setActive(binding.instProfileIconHolder,  binding.instProfileIcon,  binding.instProfileText)
        }
    }

    private fun clearNavSelection() {
        setInactive(binding.instStudentsIconHolder, binding.instStudentsIcon, binding.instStudentsText)
        setInactive(binding.instReviewIconHolder,   binding.instReviewIcon,   binding.instReviewText)
        setInactive(binding.instRequestsIconHolder, binding.instRequestsIcon, binding.instRequestsText)
        setInactive(binding.instMessagesIconHolder, binding.instMessagesIcon, binding.instMessagesText)
        setInactive(binding.instProfileIconHolder,  binding.instProfileIcon,  binding.instProfileText)
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
            .replace(binding.instFragmentContainer.id, fragment)
            .commit()
    }

    fun openDetail(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                 R.anim.slide_in_left,  R.anim.slide_out_right)
            .replace(binding.instFragmentContainer.id, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun replaceFragmentSwipe(fragment: androidx.fragment.app.Fragment, forward: Boolean) {
        val enter = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        val exit  = if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(enter, exit)
            .replace(binding.instFragmentContainer.id, fragment)
            .commit()
    }

    private fun navigateToTabIndex(index: Int) {
        val clamped = index.coerceIn(0, 4)
        if (clamped == currentTabIndex) return
        val forward = clamped > currentTabIndex
        currentTabIndex = clamped
        setHeaderVisible(true)
        setNavVisible(true)
        when (currentTabIndex) {
            0 -> { replaceFragmentSwipe(InstructorHomeFragment(), forward);     selectNavItem("students"); updateHeader("Dashboard", "Hello, Dr. Smith") }
            1 -> { replaceFragmentSwipe(InstructorReviewFragment(), forward);   selectNavItem("review");   updateHeader("Internships") }
            2 -> { replaceFragmentSwipe(InstructorRequestsFragment(), forward); selectNavItem("requests"); updateHeader("Requests") }
            3 -> { replaceFragmentSwipe(InstructorMessagesFragment(), forward); selectNavItem("messages"); updateHeader("Messages") }
            4 -> { replaceFragmentSwipe(InstructorProfileFragment(), forward);  selectNavItem("profile");  updateHeader("Profile") }
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
