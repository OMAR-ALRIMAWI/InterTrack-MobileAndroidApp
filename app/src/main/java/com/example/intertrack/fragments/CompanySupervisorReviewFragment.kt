package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.databinding.FragmentCompanySupervisorReviewBinding

/**
 * Simplified Company Review: two clear actions only — view the applications students submitted, and
 * review the company's active internships (active interns, reports, chat live in Apps/Home).
 */
class CompanySupervisorReviewFragment : Fragment() {

    private var _binding: FragmentCompanySupervisorReviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanySupervisorReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardRevApplications.setOnClickListener {
            (requireActivity() as? CompanyDashBoard)?.navigateTo("applications")
        }
        binding.cardRevInternships.setOnClickListener {
            // Dedicated company internships review/history list (ACTIVE / COMPLETED / ENDED filters),
            // each row opening the Internship Hub (COMPANY). Must NOT open the Home dashboard, and
            // is intentionally distinct from the student Applications list.
            (requireActivity() as? CompanyDashBoard)?.openDetail(CompanyInternshipsReviewFragment())
        }
        binding.cardRevOffers.setOnClickListener {
            (requireActivity() as? CompanyDashBoard)?.openDetail(ManageOffersFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Review")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
