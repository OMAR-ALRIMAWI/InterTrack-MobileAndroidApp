package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.AdminDashboardActivity
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.fragments.admin.AdminRequestsFragment
import com.example.intertrack.fragments.admin.AdminUniversityRequestsFragment
import com.example.intertrack.databinding.FragmentNotificationBinding
import com.google.firebase.auth.FirebaseAuth

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshNotifications.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshNotifications.setOnRefreshListener { loadNotifications() }
        loadNotifications()
    }

    private fun loadNotifications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressNotifications.visibility = View.VISIBLE
        binding.rvNotifications.visibility = View.GONE
        binding.tvNotificationsEmpty.visibility = View.GONE

        authRepo.getNotifications(
            recipientUid = uid,
            onSuccess = { notifications ->
                if (_binding == null) return@getNotifications
                binding.progressNotifications.visibility = View.GONE
                binding.swipeRefreshNotifications.isRefreshing = false

                if (notifications.isEmpty()) {
                    binding.tvNotificationsEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvNotifications.visibility = View.VISIBLE
                    val adapter = NotificationAdapter(notifications) { notif ->
                        if (!notif.isRead) {
                            authRepo.markNotificationRead(
                                notificationId = notif.notificationId,
                                onSuccess = { updateDashBadge(uid) },
                                onFailure = {}
                            )
                        }
                        routeNotification(notif)
                    }
                    binding.rvNotifications.adapter = adapter
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getNotifications
                binding.progressNotifications.visibility = View.GONE
                binding.swipeRefreshNotifications.isRefreshing = false
                binding.tvNotificationsEmpty.text = "Could not load notifications."
                binding.tvNotificationsEmpty.visibility = View.VISIBLE
            }
        )
    }

    private fun updateDashBadge(uid: String) {
        authRepo.hasUnreadNotifications(uid) { hasUnread ->
            if (_binding == null) return@hasUnreadNotifications
            (requireActivity() as? StudentDashBoard)?.apply { hasUnreadNotifications = hasUnread; updateUnreadBadge() }
            (requireActivity() as? InstructorDashBoard)?.apply { hasUnreadNotifications = hasUnread; updateUnreadBadge() }
            (requireActivity() as? CompanyDashBoard)?.apply { hasUnreadNotifications = hasUnread; updateUnreadBadge() }
            (requireActivity() as? AdminDashboardActivity)?.apply { hasUnreadNotifications = hasUnread; updateUnreadBadge() }
        }
    }

    /** Opens a fragment as a detail screen on whichever dashboard hosts this fragment. */
    private fun openOnHost(fragment: Fragment) {
        when (val act = requireActivity()) {
            is StudentDashBoard -> act.openDetail(fragment)
            is InstructorDashBoard -> act.openDetail(fragment)
            is CompanyDashBoard -> act.openDetail(fragment)
            is AdminDashboardActivity -> act.replaceFragment(fragment, addToBackStack = true)
        }
    }

    private fun hostRole(): String = when (requireActivity()) {
        is StudentDashBoard -> "STUDENT"
        is CompanyDashBoard -> "COMPANY"
        is InstructorDashBoard -> "INSTRUCTOR"
        is AdminDashboardActivity -> "ADMIN"
        else -> "STUDENT"
    }

    /** Routes a tapped notification to the correct screen based on its type. Safe for old data. */
    private fun routeNotification(notif: com.example.intertrack.data.model.FirestoreNotification) {
        val relatedId = notif.relatedId
        when (notif.type) {
            // Admin: a new account/verification request → open the Pending Requests screen.
            "ADMIN_ACCOUNT_REQUEST" ->
                (requireActivity() as? AdminDashboardActivity)?.apply {
                    replaceFragment(AdminRequestsFragment())
                    selectNavItem("requests")
                    updateHeader("Verification Requests")
                }

            // Admin: a university change request → open the review screen.
            "UNIVERSITY_CHANGE_REQUEST" ->
                (requireActivity() as? AdminDashboardActivity)?.apply {
                    replaceFragment(AdminUniversityRequestsFragment(), addToBackStack = true)
                    updateHeader("University Change Requests")
                }

            "REPORT_SUBMITTED", "REPORT_REVIEWED", "REPORT_REVISION_REQUESTED" ->
                if (relatedId.isNotBlank()) openOnHost(ReportDetailFragment.newInstance(relatedId))

            "NEW_MESSAGE" -> if (relatedId.isNotBlank()) openChatFromNotification(relatedId, notif.senderUid)

            "INTERNSHIP_CONNECTED",
            "INTERNSHIP_NEEDS_INSTRUCTOR_CONNECTION",
            "INTERNSHIP_APPLICATION_ACCEPTED" ->
                if (relatedId.isNotBlank())
                    openOnHost(InternshipProgressHubFragment.newInstance(relatedId, hostRole()))

            // Period set / internship completed → the student lands on My Internship (period +
            // congratulations card); company/instructor open the related Internship Hub.
            "INTERNSHIP_PERIOD_SET", "INTERNSHIP_COMPLETED" ->
                if (hostRole() == "STUDENT") {
                    openOnHost(MyInternshipFragment())
                } else if (relatedId.isNotBlank()) {
                    openOnHost(InternshipProgressHubFragment.newInstance(relatedId, hostRole()))
                }

            else -> { /* unknown/old type — stay on the notifications list, no crash */ }
        }
    }

    private fun openChatFromNotification(conversationId: String, otherUid: String) {
        if (conversationId.startsWith("progress_")) {
            // Group internship chat — ChatFragment self-normalizes the title from the connection.
            openOnHost(ChatFragment.newInstance(conversationId, "GROUP", "Internship Chat", ""))
            return
        }
        // Direct chat — resolve the other person's name for the header.
        if (otherUid.isBlank()) { openOnHost(ChatFragment.newInstance(conversationId, "", "Chat")); return }
        authRepo.fetchUserDocument(
            uid = otherUid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                openOnHost(ChatFragment.newInstance(conversationId, otherUid, user.fullName.ifBlank { "Chat" }))
            },
            onFailure = {
                if (_binding == null) return@fetchUserDocument
                openOnHost(ChatFragment.newInstance(conversationId, otherUid, "Chat"))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true); setNavVisible(true); updateHeader("Notifications")
        }
        (requireActivity() as? InstructorDashBoard)?.apply {
            setHeaderVisible(true); setNavVisible(true); updateHeader("Notifications")
        }
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true); setNavVisible(true); updateHeader("Notifications")
        }
        (requireActivity() as? AdminDashboardActivity)?.updateHeader("Notifications")
        loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
