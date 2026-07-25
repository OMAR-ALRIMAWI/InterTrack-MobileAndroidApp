package com.example.intertrack.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.AdminDashboardActivity
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentAdminHomeBinding

class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()
    private lateinit var activityAdapter: RecentActivityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activityAdapter = RecentActivityAdapter()
        binding.rvRecentActivity.layoutManager = LinearLayoutManager(context)
        binding.rvRecentActivity.adapter = activityAdapter
        binding.rvRecentActivity.isNestedScrollingEnabled = false

        binding.btnQuickViewRequests.setOnClickListener {
            (requireActivity() as? AdminDashboardActivity)?.apply {
                replaceFragment(AdminRequestsFragment())
                selectNavItem("requests")
                updateHeader("Verification Requests")
            }
        }

        binding.btnQuickManageAccounts.setOnClickListener {
            (requireActivity() as? AdminDashboardActivity)?.apply {
                replaceFragment(AdminAccountsFragment())
                selectNavItem("accounts")
                updateHeader("Manage Accounts")
            }
        }

        binding.btnQuickUniversityRequests.setOnClickListener {
            (requireActivity() as? AdminDashboardActivity)?.apply {
                replaceFragment(AdminUniversityRequestsFragment(), addToBackStack = true)
                updateHeader("University Change Requests")
            }
        }

        binding.btnRefreshHome.setOnClickListener {
            loadStats()
            loadRecentActivity()
        }

        loadStats()
        loadRecentActivity()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        loadRecentActivity()
    }

    private fun loadStats() {
        binding.statsGroup.visibility = View.GONE
        binding.statsLoading.visibility = View.VISIBLE

        authRepo.fetchAdminStats(
            onSuccess = { stats ->
                if (_binding == null) return@fetchAdminStats
                binding.statsLoading.visibility = View.GONE
                binding.statsGroup.visibility = View.VISIBLE
                binding.tvStatTotal.text = stats.totalAccounts.toString()
                binding.tvStatStudents.text = stats.totalStudents.toString()
                binding.tvStatInstructors.text = stats.totalInstructors.toString()
                binding.tvStatCompanies.text = stats.totalCompanySupervisors.toString()
                binding.tvStatPending.text = stats.pendingRequests.toString()
                binding.tvStatBlocked.text = stats.blockedAccounts.toString()
            },
            onFailure = { message ->
                if (_binding == null) return@fetchAdminStats
                binding.statsLoading.visibility = View.GONE
                binding.statsGroup.visibility = View.VISIBLE
                Toast.makeText(context, "Stats error: $message", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadRecentActivity() {
        binding.activityLoading.visibility = View.VISIBLE
        binding.rvRecentActivity.visibility = View.GONE
        binding.tvActivityEmpty.visibility = View.GONE

        authRepo.fetchRecentActivity(
            onSuccess = { logs ->
                if (_binding == null) return@fetchRecentActivity
                binding.activityLoading.visibility = View.GONE
                if (logs.isEmpty()) {
                    binding.tvActivityEmpty.visibility = View.VISIBLE
                    binding.rvRecentActivity.visibility = View.GONE
                } else {
                    binding.tvActivityEmpty.visibility = View.GONE
                    binding.rvRecentActivity.visibility = View.VISIBLE
                    activityAdapter.submitList(logs)
                }
            },
            onFailure = { message ->
                if (_binding == null) return@fetchRecentActivity
                binding.activityLoading.visibility = View.GONE
                binding.tvActivityEmpty.visibility = View.VISIBLE
                binding.tvActivityEmpty.text = "Could not load activity: $message"
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
