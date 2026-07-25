package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.R
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentCompanySupervisorCompanyBinding
import com.google.firebase.auth.FirebaseAuth

class CompanySupervisorCompanyFragment : Fragment() {

    private var _binding: FragmentCompanySupervisorCompanyBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanySupervisorCompanyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCompanyOffers.layoutManager = LinearLayoutManager(requireContext())

        binding.btnEditCompanyProfile.setOnClickListener {
            val frag = EditCompanyProfileFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, frag)
                .addToBackStack(null)
                .commit()
        }

        binding.btnAddPosition.setOnClickListener {
            val frag = PublishOfferFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, frag)
                .addToBackStack(null)
                .commit()
        }

        loadCompanyProfile()
    }

    private fun loadCompanyProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                val companyId = user.companyId

                if (companyId.isNullOrBlank()) {
                    binding.tvCompanyName.text = "No company set up yet"
                    binding.tvCompanyIndustry.text = "Tap Edit to create your company profile"
                    binding.tvOffersEmpty.text = "Set up a company profile first."
                    binding.tvOffersEmpty.visibility = View.VISIBLE
                    return@fetchUserDocument
                }

                authRepo.fetchCompany(
                    companyId = companyId,
                    onSuccess = { company ->
                        if (_binding == null) return@fetchCompany
                        if (company != null) {
                            val initial = company.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                            binding.tvCompanyInitial.text = initial
                            binding.tvCompanyName.text = company.name.ifBlank { "Your Company" }
                            val industryCity = buildString {
                                if (company.industry.isNotBlank()) append(company.industry)
                                if (company.industry.isNotBlank() && company.city.isNotBlank()) append(" · ")
                                if (company.city.isNotBlank()) append(company.city)
                            }
                            binding.tvCompanyIndustry.text = industryCity.ifBlank { "—" }
                            binding.tvCompanySize.text = company.size.ifBlank { "" }
                            binding.tvCompanyAbout.text = company.description.ifBlank { "No description provided." }
                        }
                    },
                    onFailure = { /* ignore — company name stays placeholder */ }
                )

                loadActiveOffers(uid)
            },
            onFailure = { _ ->
                if (_binding == null) return@fetchUserDocument
                Toast.makeText(requireContext(), "Could not load your profile.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadActiveOffers(supervisorUid: String) {
        binding.progressOffers.visibility = View.VISIBLE
        binding.tvOffersEmpty.visibility = View.GONE
        binding.rvCompanyOffers.visibility = View.GONE

        authRepo.getCompanyActiveOffers(
            supervisorUid = supervisorUid,
            onSuccess = { offers ->
                if (_binding == null) return@getCompanyActiveOffers
                binding.progressOffers.visibility = View.GONE

                if (offers.isEmpty()) {
                    binding.tvOffersEmpty.text = "No active offers. Tap + Add to publish one."
                    binding.tvOffersEmpty.visibility = View.VISIBLE
                    binding.rvCompanyOffers.visibility = View.GONE
                } else {
                    binding.tvOffersEmpty.visibility = View.GONE
                    binding.rvCompanyOffers.visibility = View.VISIBLE
                    val adapter = InternshipOfferAdapter(
                        items = offers,
                        onItemClick = { offer ->
                            Toast.makeText(requireContext(),
                                "${offer.title}: ${offer.status}", Toast.LENGTH_SHORT).show()
                        }
                    )
                    binding.rvCompanyOffers.adapter = adapter
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getCompanyActiveOffers
                binding.progressOffers.visibility = View.GONE
                binding.tvOffersEmpty.text = "Could not load offers. Please try again."
                binding.tvOffersEmpty.visibility = View.VISIBLE
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Company Profile")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
