package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentEditOfferBinding

class EditOfferFragment : Fragment() {

    private var _binding: FragmentEditOfferBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    companion object {
        private const val ARG_OFFER_ID     = "offer_id"
        private const val ARG_TITLE        = "title"
        private const val ARG_DEPARTMENT   = "department"
        private const val ARG_DESCRIPTION  = "description"
        private const val ARG_REQUIREMENTS = "requirements"
        private const val ARG_DURATION     = "duration"
        private const val ARG_SEATS        = "seats"

        fun newInstance(
            offerId: String,
            title: String,
            department: String,
            description: String,
            requirements: String,
            duration: String,
            seats: Int
        ): EditOfferFragment = EditOfferFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_OFFER_ID, offerId)
                putString(ARG_TITLE, title)
                putString(ARG_DEPARTMENT, department)
                putString(ARG_DESCRIPTION, description)
                putString(ARG_REQUIREMENTS, requirements)
                putString(ARG_DURATION, duration)
                putInt(ARG_SEATS, seats)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditOfferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            binding.etEditOfferTitle.setText(args.getString(ARG_TITLE, ""))
            binding.etEditOfferDepartment.setText(args.getString(ARG_DEPARTMENT, ""))
            binding.etEditOfferDescription.setText(args.getString(ARG_DESCRIPTION, ""))
            binding.etEditOfferRequirements.setText(args.getString(ARG_REQUIREMENTS, ""))
            binding.etEditOfferDuration.setText(args.getString(ARG_DURATION, ""))
            val seats = args.getInt(ARG_SEATS, 0)
            if (seats > 0) binding.etEditOfferSeats.setText(seats.toString())
        }

        binding.btnSaveOffer.setOnClickListener {
            val title = binding.etEditOfferTitle.text?.toString()?.trim() ?: ""
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Position title is required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveOffer()
        }

        binding.btnCancelEditOffer.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun saveOffer() {
        val offerId = arguments?.getString(ARG_OFFER_ID) ?: return
        binding.btnSaveOffer.isEnabled = false
        binding.btnSaveOffer.text = "Saving…"

        val seats = binding.etEditOfferSeats.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val updates: Map<String, Any?> = mapOf(
            "title"        to (binding.etEditOfferTitle.text?.toString()?.trim() ?: ""),
            "department"   to (binding.etEditOfferDepartment.text?.toString()?.trim() ?: ""),
            "description"  to (binding.etEditOfferDescription.text?.toString()?.trim() ?: ""),
            "requirements" to (binding.etEditOfferRequirements.text?.toString()?.trim() ?: ""),
            "duration"     to (binding.etEditOfferDuration.text?.toString()?.trim() ?: ""),
            "seats"        to seats
        )

        authRepo.updateInternshipOffer(
            offerId = offerId,
            updates = updates,
            onSuccess = {
                if (_binding == null) return@updateInternshipOffer
                Toast.makeText(requireContext(), "Offer updated.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { _ ->
                if (_binding == null) return@updateInternshipOffer
                binding.btnSaveOffer.isEnabled = true
                binding.btnSaveOffer.text = "Save Changes"
                Toast.makeText(requireContext(),
                    "Could not save changes. Please try again.", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Edit Offer")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
