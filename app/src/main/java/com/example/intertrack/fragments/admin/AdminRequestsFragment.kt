package com.example.intertrack.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.AdminDashboardActivity
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.PendingUser
import com.example.intertrack.databinding.FragmentAdminRequestsBinding

class AdminRequestsFragment : Fragment() {

    private var _binding: FragmentAdminRequestsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()
    private lateinit var adapter: PendingRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PendingRequestAdapter { user ->
            openRequestDetail(user)
        }
        binding.rvRequests.layoutManager = LinearLayoutManager(context)
        binding.rvRequests.adapter = adapter

        binding.btnRefreshRequests.setOnClickListener { loadPendingUsers() }

        binding.swipeRefreshAdminRequests.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshAdminRequests.setOnRefreshListener { loadPendingUsers() }

        loadPendingUsers()
    }

    override fun onResume() {
        super.onResume()
        loadPendingUsers()
    }

    fun loadPendingUsers() {
        showLoading()
        authRepo.fetchPendingUsers(
            onSuccess = { list ->
                if (_binding == null) return@fetchPendingUsers
                adapter.submitList(list)
                binding.tvRequestsCount.text = "Pending Requests (${list.size})"
                when {
                    list.isEmpty() -> showEmpty()
                    else -> showList()
                }
            },
            onFailure = { message ->
                if (_binding == null) return@fetchPendingUsers
                showError(message)
            }
        )
    }

    private fun openRequestDetail(user: PendingUser) {
        val detail = AdminRequestDetailFragment.newInstance(
            uid = user.uid,
            fullName = user.fullName,
            email = user.email,
            role = user.role
        )
        (requireActivity() as? AdminDashboardActivity)?.apply {
            replaceFragment(detail, addToBackStack = true)
            updateHeader("Request Details")
        }
    }

    private fun showLoading() {
        // Skip the center spinner during a pull-to-refresh (the swipe spinner already shows).
        binding.requestsProgressBar.visibility =
            if (binding.swipeRefreshAdminRequests.isRefreshing) View.GONE else View.VISIBLE
        binding.rvRequests.visibility = View.GONE
        binding.requestsEmptyState.visibility = View.GONE
        binding.requestsErrorState.visibility = View.GONE
    }

    private fun showList() {
        binding.requestsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminRequests.isRefreshing = false
        binding.rvRequests.visibility = View.VISIBLE
        binding.requestsEmptyState.visibility = View.GONE
        binding.requestsErrorState.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.requestsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminRequests.isRefreshing = false
        binding.rvRequests.visibility = View.GONE
        binding.requestsEmptyState.visibility = View.VISIBLE
        binding.requestsErrorState.visibility = View.GONE
        binding.tvRequestsCount.text = "Pending Requests (0)"
    }

    private fun showError(message: String) {
        binding.requestsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminRequests.isRefreshing = false
        binding.rvRequests.visibility = View.GONE
        binding.requestsEmptyState.visibility = View.GONE
        binding.requestsErrorState.visibility = View.VISIBLE
        binding.tvRequestsError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
