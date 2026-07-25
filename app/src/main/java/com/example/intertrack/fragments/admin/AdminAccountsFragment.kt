package com.example.intertrack.fragments.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.FragmentAdminAccountsBinding

class AdminAccountsFragment : Fragment(), AdminAccountDetailSheet.OnActionListener {

    private var _binding: FragmentAdminAccountsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()
    private lateinit var adapter: AllUsersAdapter
    private var allUsers: List<User> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AllUsersAdapter { user ->
            AdminAccountDetailSheet.newInstance(user)
                .show(childFragmentManager, "account_detail")
        }
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        binding.rvAccounts.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilters()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.chipAll.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipStudents.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipInstructors.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipCompanies.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipStatusAll.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipStatusActive.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipStatusPending.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.chipStatusBlocked.setOnCheckedChangeListener { _, _ -> applyFilters() }

        binding.btnRefreshAccounts.setOnClickListener { loadAllUsers() }

        binding.swipeRefreshAdminAccounts.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshAdminAccounts.setOnRefreshListener { loadAllUsers() }

        loadAllUsers()
    }

    override fun onAccountActionPerformed() {
        loadAllUsers()
    }

    fun loadAllUsers() {
        showLoading()
        authRepo.fetchAllUsers(
            onSuccess = { users ->
                if (_binding == null) return@fetchAllUsers
                allUsers = users
                applyFilters()
                if (users.isEmpty()) showEmpty() else showList()
            },
            onFailure = { message ->
                if (_binding == null) return@fetchAllUsers
                showError(message)
            }
        )
    }

    private fun applyFilters() {
        if (_binding == null) return
        val query = binding.etSearch.text?.toString()?.trim()?.lowercase() ?: ""

        val roleFilter: String? = when {
            binding.chipStudents.isChecked    -> UserRole.STUDENT.value
            binding.chipInstructors.isChecked -> UserRole.INSTRUCTOR.value
            binding.chipCompanies.isChecked   -> UserRole.COMPANY_SUPERVISOR.value
            else -> null
        }

        val statusFilter: String? = when {
            binding.chipStatusActive.isChecked  -> AccountStatus.ACTIVE.value
            binding.chipStatusPending.isChecked -> AccountStatus.PENDING.value
            binding.chipStatusBlocked.isChecked -> AccountStatus.BLOCKED.value
            else -> null
        }

        val filtered = allUsers.filter { user ->
            val matchesQuery = query.isEmpty() ||
                user.fullName.lowercase().contains(query) ||
                user.email.lowercase().contains(query)
            val matchesRole = roleFilter == null || user.role == roleFilter
            val matchesStatus = statusFilter == null || user.accountStatus == statusFilter
            matchesQuery && matchesRole && matchesStatus
        }

        adapter.submitList(filtered)
        binding.tvAccountsCount.text = "Accounts (${filtered.size})"

        val hasResults = filtered.isNotEmpty()
        binding.tvNoResults.visibility = if (!hasResults && allUsers.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvAccounts.visibility = if (hasResults) View.VISIBLE else View.GONE
    }

    private fun showLoading() {
        // Skip the center spinner during a pull-to-refresh (the swipe spinner already shows).
        binding.accountsProgressBar.visibility =
            if (binding.swipeRefreshAdminAccounts.isRefreshing) View.GONE else View.VISIBLE
        binding.rvAccounts.visibility = View.GONE
        binding.accountsEmptyState.visibility = View.GONE
        binding.accountsErrorState.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE
    }

    private fun showList() {
        binding.accountsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminAccounts.isRefreshing = false
        binding.accountsEmptyState.visibility = View.GONE
        binding.accountsErrorState.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.accountsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminAccounts.isRefreshing = false
        binding.rvAccounts.visibility = View.GONE
        binding.accountsEmptyState.visibility = View.VISIBLE
        binding.accountsErrorState.visibility = View.GONE
        binding.tvAccountsCount.text = "Accounts (0)"
    }

    private fun showError(message: String) {
        binding.accountsProgressBar.visibility = View.GONE
        binding.swipeRefreshAdminAccounts.isRefreshing = false
        binding.rvAccounts.visibility = View.GONE
        binding.accountsEmptyState.visibility = View.GONE
        binding.accountsErrorState.visibility = View.VISIBLE
        binding.tvErrorAccounts.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
