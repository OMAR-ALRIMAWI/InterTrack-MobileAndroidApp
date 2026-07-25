package com.example.intertrack.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.LoginActivity
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreCompany
import com.example.intertrack.data.model.User
import com.example.intertrack.databinding.FragmentCompanySupervisorProfileBinding
import com.google.firebase.auth.FirebaseAuth

class CompanySupervisorProfileFragment : Fragment() {

    private var _binding: FragmentCompanySupervisorProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanySupervisorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfile()

        binding.swipeRefreshProfile.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshProfile.setOnRefreshListener { loadProfile() }

        val dash = requireActivity() as? CompanyDashBoard

        binding.btnProfEditProfile.setOnClickListener {
            dash?.openDetail(EditCompanyProfileFragment())
        }

        binding.btnProfPublishOffer.setOnClickListener {
            dash?.openDetail(PublishOfferFragment())
        }

        binding.btnSupLogout.setOnClickListener {
            AppSessionCache.clear()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "CompanySupervisorProfileFragment -> Loading")
        binding.progressSupProfileMain.visibility = View.VISIBLE
        binding.supProfileContent.visibility = View.GONE
        binding.tvSupProfileError.visibility = View.GONE

        binding.tvSupName.text = ""
        binding.tvSupEmail.text = ""
        binding.tvProfCompanyName.text = ""
        binding.tvProfCompanyIndustry.text = ""
        binding.tvProfAbout.text = ""
        binding.tvSupDept.text = ""
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "CompanySupervisorProfileFragment -> Content")
        binding.progressSupProfileMain.visibility = View.GONE
        binding.supProfileContent.visibility = View.VISIBLE
        binding.tvSupProfileError.visibility = View.GONE
        binding.swipeRefreshProfile.isRefreshing = false
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "CompanySupervisorProfileFragment -> Error: $message")
        binding.progressSupProfileMain.visibility = View.GONE
        binding.supProfileContent.visibility = View.GONE
        binding.tvSupProfileError.visibility = View.VISIBLE
        binding.tvSupProfileError.text = message
        binding.swipeRefreshProfile.isRefreshing = false
    }

    private fun bindSupervisorUser(user: User) {
        binding.tvSupName.text = user.fullName.ifBlank { "Supervisor" }
        binding.tvProfSupInitials.text = user.initials()
        binding.tvSupRole.text = user.position?.takeIf { it.isNotBlank() } ?: "Company Supervisor"
        binding.tvSupEmail.text = user.email
        binding.tvSupDept.text = user.department?.takeIf { it.isNotBlank() } ?: "Department not set"
    }

    private fun bindSupervisorCompany(company: FirestoreCompany) {
        binding.tvProfInitials.text = company.name.firstOrNull()?.toString()?.uppercase() ?: "?"
        binding.tvProfCompanyName.text = company.name.ifBlank { "Company" }
        val locationStr = if (company.city.isNotBlank()) {
            "${company.industry} · ${company.city}"
        } else {
            company.industry
        }
        binding.tvProfCompanyIndustry.text = locationStr.ifBlank { "—" }
        binding.tvProfAbout.text = company.description.ifBlank { "No description provided." }
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        showLoadingState()

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                AppSessionCache.currentUser = user
                bindSupervisorUser(user)

                val companyId = user.companyId
                if (!companyId.isNullOrBlank()) {
                    loadCompanyData(companyId)
                } else {
                    binding.tvProfCompanyName.text = "Company not set"
                    binding.tvProfCompanyIndustry.text = "Edit profile to add company details"
                    binding.tvProfInitials.text = "?"
                    binding.tvProfAbout.text = "No company description yet."
                    showContentState()
                }
            },
            onFailure = { message ->
                if (_binding == null) return@fetchUserDocument
                showErrorState(message)
            }
        )
    }

    private fun loadCompanyData(companyId: String) {
        authRepo.fetchCompany(
            companyId = companyId,
            onSuccess = { company ->
                if (_binding == null) return@fetchCompany
                if (company == null) {
                    binding.tvProfCompanyName.text = "Company not found"
                    showContentState()
                    return@fetchCompany
                }
                AppSessionCache.currentCompany = company
                bindSupervisorCompany(company)
                showContentState()
            },
            onFailure = { message ->
                if (_binding == null) return@fetchCompany
                showErrorState(message)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Profile")
        }
        loadProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
