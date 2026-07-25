package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.databinding.FragmentPublishOfferBinding
import com.google.firebase.auth.FirebaseAuth

class PublishOfferFragment : Fragment() {

    private var _binding: FragmentPublishOfferBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublishOfferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPublishOffer.setOnClickListener {
            val title = binding.etOfferTitle.text?.toString()?.trim() ?: ""
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a position title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            publishOffer()
        }

        binding.btnCancelOffer.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun publishOffer() {
        val supervisorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.btnPublishOffer.isEnabled = false
        binding.btnPublishOffer.text = "Publishing…"

        authRepo.fetchUserDocument(
            uid = supervisorUid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                val companyId = user.companyId ?: ""

                if (companyId.isBlank()) {
                    binding.btnPublishOffer.isEnabled = true
                    binding.btnPublishOffer.text = "Publish Offer"
                    Toast.makeText(requireContext(),
                        "No company profile linked. Please set up your company first.",
                        Toast.LENGTH_LONG).show()
                    return@fetchUserDocument
                }

                authRepo.fetchCompany(
                    companyId = companyId,
                    onSuccess = { company ->
                        if (_binding == null) return@fetchCompany
                        val companyName = company?.name ?: ""
                        val seatsText = binding.etOfferMaxApps.text?.toString()?.trim() ?: ""
                        val seats = seatsText.toIntOrNull() ?: 0

                        val offer = FirestoreInternshipOffer(
                            supervisorUid = supervisorUid,
                            companyId = companyId,
                            companyName = companyName,
                            supervisorName = user.fullName,
                            title = binding.etOfferTitle.text?.toString()?.trim() ?: "",
                            department = binding.etOfferDepartment.text?.toString()?.trim() ?: "",
                            description = binding.etOfferDescription.text?.toString()?.trim() ?: "",
                            requirements = binding.etOfferRequirements.text?.toString()?.trim() ?: "",
                            duration = binding.etOfferDuration.text?.toString()?.trim() ?: "",
                            seats = seats,
                            status = "OPEN"
                        )

                        authRepo.publishInternshipOffer(
                            offer = offer,
                            onSuccess = { _ ->
                                if (_binding == null) return@publishInternshipOffer
                                Toast.makeText(requireContext(),
                                    "Offer published successfully!", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            },
                            onFailure = { _ ->
                                if (_binding == null) return@publishInternshipOffer
                                binding.btnPublishOffer.isEnabled = true
                                binding.btnPublishOffer.text = "Publish Offer"
                                Toast.makeText(requireContext(),
                                    "Failed to publish offer. Please try again.",
                                    Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onFailure = { _ ->
                        if (_binding == null) return@fetchCompany
                        binding.btnPublishOffer.isEnabled = true
                        binding.btnPublishOffer.text = "Publish Offer"
                        Toast.makeText(requireContext(),
                            "Could not load company details. Please try again.",
                            Toast.LENGTH_LONG).show()
                    }
                )
            },
            onFailure = { _ ->
                if (_binding == null) return@fetchUserDocument
                binding.btnPublishOffer.isEnabled = true
                binding.btnPublishOffer.text = "Publish Offer"
                Toast.makeText(requireContext(),
                    "Could not load your profile. Please try again.",
                    Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Publish Offer")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
