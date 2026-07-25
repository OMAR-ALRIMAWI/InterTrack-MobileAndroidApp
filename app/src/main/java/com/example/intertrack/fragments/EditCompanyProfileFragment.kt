package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreCompany
import com.example.intertrack.databinding.FragmentEditCompanyProfileBinding
import com.google.firebase.auth.FirebaseAuth

class EditCompanyProfileFragment : Fragment() {

    private var _binding: FragmentEditCompanyProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var currentCompanyId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditCompanyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentCompany()

        binding.btnChangeCompLogo.setOnClickListener {
            Toast.makeText(requireContext(), "Logo upload — coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveCompProfile.setOnClickListener { saveCompany() }

        binding.btnCancelCompProfile.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadCurrentCompany() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                // Pre-fill contact email from user account
                binding.etCompEmail.setText(user.email)

                val companyId = user.companyId
                if (!companyId.isNullOrBlank()) {
                    currentCompanyId = companyId
                    authRepo.fetchCompany(
                        companyId = companyId,
                        onSuccess = { company ->
                            if (_binding == null || company == null) return@fetchCompany
                            binding.etCompName.setText(company.name)
                            binding.etCompIndustry.setText(company.industry)
                            binding.etCompLocation.setText(company.city)
                            binding.etCompDescription.setText(company.description)
                        },
                        onFailure = { /* fail silently — fields stay blank */ }
                    )
                }
            },
            onFailure = { /* fail silently */ }
        )
    }

    private fun saveCompany() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val name = binding.etCompName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.etCompName.error = "Company name cannot be empty"
            return
        }

        binding.btnSaveCompProfile.isEnabled = false
        binding.btnSaveCompProfile.text = "Saving…"

        val data = mapOf<String, Any?>(
            "name" to name,
            "industry" to (binding.etCompIndustry.text?.toString()?.trim() ?: ""),
            "city" to (binding.etCompLocation.text?.toString()?.trim() ?: ""),
            "description" to (binding.etCompDescription.text?.toString()?.trim() ?: "")
        )

        authRepo.saveCompany(
            companyId = currentCompanyId,
            supervisorUid = uid,
            data = data,
            onSuccess = { newId ->
                if (_binding == null) return@saveCompany
                currentCompanyId = newId
                // Patch company cache so supervisor profile shows updated data instantly
                AppSessionCache.currentCompany = (AppSessionCache.currentCompany ?: FirestoreCompany()).copy(
                    companyId = newId,
                    name = data["name"] as? String ?: "",
                    industry = data["industry"] as? String ?: "",
                    city = data["city"] as? String ?: "",
                    description = data["description"] as? String ?: ""
                )
                binding.btnSaveCompProfile.isEnabled = true
                binding.btnSaveCompProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Company profile saved", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { msg ->
                if (_binding == null) return@saveCompany
                binding.btnSaveCompProfile.isEnabled = true
                binding.btnSaveCompProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Edit Profile")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
