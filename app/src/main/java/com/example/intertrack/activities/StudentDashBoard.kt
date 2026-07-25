package com.example.intertrack.activities

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreNotification
import com.example.intertrack.databinding.ActivityStudentDashBoardBinding
import com.example.intertrack.fragments.MessegesStudent
import com.example.intertrack.fragments.NotificationFragment
import com.example.intertrack.fragments.StudentInternshipsFragment
import com.example.intertrack.fragments.PopupNotificationAdapter
import com.example.intertrack.fragments.ProfileStudent
import com.example.intertrack.fragments.ReportsStudent
import com.example.intertrack.fragments.StudentHomeFragment
import com.example.intertrack.fragments.VerifyStudentFragment
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

class StudentDashBoard : AppCompatActivity() {

    private lateinit var binding: ActivityStudentDashBoardBinding
    private val authRepo = FirebaseAuthRepository()

    private val activeColor       = Color.parseColor("#0569bf")
    private val inactiveColor     = Color.parseColor("#B8B2C4")
    private val inactiveTextColor = Color.parseColor("#9CA3AF")

    private var notificationPopup: PopupWindow? = null
    var hasUnreadNotifications = false
    private var currentTabIndex = 0
    private lateinit var swipeDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.navigationBarColor = Color.parseColor("#F6F9FC")

        val showVerification = intent.getBooleanExtra("showVerification", false)

        if (savedInstanceState == null) {
            if (showVerification) {
                replaceFragment(VerifyStudentFragment())
                setHeaderVisible(false)
                setNavVisible(false)
            } else {
                replaceFragment(StudentHomeFragment())
                selectNavItem("home")
                updateHeader("Home", AppSessionCache.currentUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" } ?: "Hello 👋")
            }
        }

        updateUnreadBadge()
        refreshUnreadBadgeFromFirestore()
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
        refreshUnreadBadgeFromFirestore()
        checkUserStillActive()
    }

    // ── Public API for fragments ────────────────────────────────────────────

    fun setHeaderVisible(visible: Boolean) {
        binding.sharedHeader.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            binding.fragmentContainer.setBackgroundResource(R.drawable.bg_body_top_rounded)
        } else {
            binding.fragmentContainer.background = null
        }
    }

    fun setNavVisible(visible: Boolean) {
        binding.bottomNavContainer.visibility = if (visible) View.VISIBLE else View.GONE
        binding.centerFab.visibility          = if (visible) View.VISIBLE else View.GONE
    }

    fun updateHeader(title: String, subtitle: String? = null) {
        binding.tvPageTitle.text = title
        if (subtitle != null) {
            binding.tvPageSubtitle.text = subtitle
            binding.tvPageSubtitle.visibility = View.VISIBLE
        } else {
            binding.tvPageSubtitle.visibility = View.GONE
        }
    }

    fun loadHome() {
        replaceFragment(StudentHomeFragment())
        selectNavItem("home")
        updateHeader("Home", AppSessionCache.currentUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" } ?: "Hello 👋")
        setHeaderVisible(true)
        setNavVisible(true)
    }

    // ── Unread badge ──────────────────────────────────────────────────────────

    private var bellPulse: android.animation.Animator? = null

    fun updateUnreadBadge() {
        binding.notifUnreadDot.visibility =
            if (hasUnreadNotifications) View.VISIBLE else View.GONE
        if (hasUnreadNotifications) startBellPulse() else stopBellPulse()
    }

    private fun startBellPulse() {
        if (bellPulse != null) return
        val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.notificationIcon,
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
        binding.notificationIcon.apply { scaleX = 1f; scaleY = 1f; rotation = 0f }
    }

    private fun refreshUnreadBadgeFromFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.hasUnreadNotifications(uid) { hasUnread ->
            hasUnreadNotifications = hasUnread
            updateUnreadBadge()
        }
    }

    // ── Deleted-user guard ────────────────────────────────────────────────────

    private fun checkUserStillActive() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            forceLogout()
            return
        }
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (user.isDeleted() || user.isBlocked()) forceLogout()
            },
            onFailure = { /* network issue — don't force logout */ }
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

    // ── Navigation wiring ─────────────────────────────────────────────────

    private fun addNavigationListeners() {

        binding.navHome.setOnClickListener {
            currentTabIndex = 0
            replaceFragment(StudentHomeFragment())
            selectNavItem("home")
            updateHeader("Home", AppSessionCache.currentUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" } ?: "Hello 👋")
        }

        binding.navReports.setOnClickListener {
            currentTabIndex = 1
            replaceFragment(ReportsStudent())
            selectNavItem("reports")
            updateHeader("My Reports")
        }

        binding.navMessages.setOnClickListener {
            currentTabIndex = 2
            replaceFragment(MessegesStudent())
            selectNavItem("messages")
            updateHeader("Messages")
        }

        binding.navProfile.setOnClickListener {
            currentTabIndex = 3
            replaceFragment(ProfileStudent())
            selectNavItem("profile")
            updateHeader("Profile")
        }

        binding.centerFab.setOnClickListener { v ->
            animateFabPress(v)
            currentTabIndex = -1
            replaceFragment(StudentInternshipsFragment())
            clearNavSelection()
            updateHeader("Internships")
        }

        binding.notificationWrap.setOnClickListener { v ->
            animateButtonPress(v)
            if (notificationPopup?.isShowing == true) {
                dismissNotificationPopup()
            } else {
                showNotificationPopup()
            }
        }
    }

    // ── Notification popup ───────────────────────────────────────────────

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

        val rvPopup     = popupView.findViewById<RecyclerView>(R.id.rvPopupNotifications)
        val progress    = popupView.findViewById<View>(R.id.popupNotifProgress)
        val emptyView   = popupView.findViewById<TextView>(R.id.tvPopupNotifEmpty)
        val markAllBtn  = popupView.findViewById<TextView>(R.id.btnMarkAllRead)

        rvPopup.layoutManager = LinearLayoutManager(this)

        var loadedNotifs: List<FirestoreNotification> = emptyList()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            authRepo.getNotifications(
                recipientUid = uid,
                onSuccess = { all ->
                    val items = all.take(5)
                    loadedNotifs = items
                    progress.visibility = View.GONE
                    if (items.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        rvPopup.visibility = View.VISIBLE
                        rvPopup.adapter = PopupNotificationAdapter(items) {
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
                    emptyView.text = "Could not load notifications"
                    emptyView.visibility = View.VISIBLE
                }
            )
        } else {
            progress.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        }

        markAllBtn.setOnClickListener {
            val unread = loadedNotifs.filter { !it.isRead }
            unread.forEach { n ->
                authRepo.markNotificationRead(n.notificationId, {}, {})
            }
            hasUnreadNotifications = false
            updateUnreadBadge()
            (rvPopup.adapter as? PopupNotificationAdapter)?.markAllRead()
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
            binding.notificationWrap,
            binding.notificationWrap.width - popupWidth,
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

    // ── Press animations ─────────────────────────────────────────────────

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

    private fun animateFabPress(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100)
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }.start()
            }.start()
    }

    // ── Nav selection helpers ─────────────────────────────────────────────

    private fun selectNavItem(selected: String) {
        clearNavSelection()
        when (selected) {
            "home"     -> setActive(binding.homeIconHolder,     binding.homeIcon,     binding.homeText)
            "reports"  -> setActive(binding.reportsIconHolder,  binding.reportsIcon,  binding.reportsText)
            "messages" -> setActive(binding.messagesIconHolder, binding.messagesIcon, binding.messagesText)
            "profile"  -> setActive(binding.profileIconHolder,  binding.profileIcon,  binding.profileText)
        }
    }

    private fun clearNavSelection() {
        setInactive(binding.homeIconHolder,     binding.homeIcon,     binding.homeText)
        setInactive(binding.reportsIconHolder,  binding.reportsIcon,  binding.reportsText)
        setInactive(binding.messagesIconHolder, binding.messagesIcon, binding.messagesText)
        setInactive(binding.profileIconHolder,  binding.profileIcon,  binding.profileText)
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

    private fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }

    fun openDetail(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                 R.anim.slide_in_left,  R.anim.slide_out_right)
            .replace(binding.fragmentContainer.id, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun replaceFragmentSwipe(fragment: androidx.fragment.app.Fragment, forward: Boolean) {
        val enter = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        val exit  = if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(enter, exit)
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }

    private fun navigateToTabIndex(index: Int) {
        val clamped = index.coerceIn(0, 3)
        if (clamped == currentTabIndex) return
        val forward = clamped > currentTabIndex
        currentTabIndex = clamped
        setHeaderVisible(true)
        setNavVisible(true)
        when (currentTabIndex) {
            0 -> { replaceFragmentSwipe(StudentHomeFragment(), forward); selectNavItem("home");    updateHeader("Home", AppSessionCache.currentUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" } ?: "Hello 👋") }
            1 -> { replaceFragmentSwipe(ReportsStudent(), forward);      selectNavItem("reports"); updateHeader("My Reports") }
            2 -> { replaceFragmentSwipe(MessegesStudent(), forward);     selectNavItem("messages"); updateHeader("Messages") }
            3 -> { replaceFragmentSwipe(ProfileStudent(), forward);      selectNavItem("profile"); updateHeader("Profile") }
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
                if (currentTabIndex < 0) return false
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
