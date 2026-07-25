package com.example.intertrack.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.databinding.FragmentInternshipOfferDetailsBinding
import com.google.firebase.auth.FirebaseAuth

class InternshipOfferDetailsFragment : Fragment() {

    private var _binding: FragmentInternshipOfferDetailsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()
    // Live-refreshed application status; starts from the argument-snapshot and is re-checked on
    // every onResume so pressing Back from Apply flips the button out of "Apply Now".
    private var currentAppStatus: String = ""
    // Live-resolved company name (owner-truth). Falls back to the argument snapshot until the
    // supervisorUid-based lookup returns — avoids showing a stale "TechCorp Turkey" snapshot when
    // the current supervisor's real company is different.
    private var liveCompanyName: String = ""

    companion object {
        private const val ARG_OFFER_ID          = "offer_id"
        private const val ARG_OFFER_TITLE       = "offer_title"
        private const val ARG_OFFER_DEPT        = "offer_dept"
        private const val ARG_OFFER_DURATION    = "offer_duration"
        private const val ARG_OFFER_SEATS       = "offer_seats"
        private const val ARG_OFFER_DESC        = "offer_desc"
        private const val ARG_OFFER_REQS        = "offer_reqs"
        private const val ARG_COMPANY_ID        = "company_id"
        private const val ARG_COMPANY_NAME      = "company_name"
        private const val ARG_SUPERVISOR_UID    = "supervisor_uid"
        private const val ARG_APP_STATUS        = "app_status"

        fun newInstance(
            offer: FirestoreInternshipOffer,
            companyId: String,
            companyName: String,
            supervisorUid: String,
            applicationStatus: String
        ): InternshipOfferDetailsFragment {
            return InternshipOfferDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_OFFER_ID,       offer.offerId)
                    putString(ARG_OFFER_TITLE,    offer.title)
                    putString(ARG_OFFER_DEPT,     offer.department)
                    putString(ARG_OFFER_DURATION, offer.duration)
                    putInt(ARG_OFFER_SEATS,       offer.seats)
                    putString(ARG_OFFER_DESC,     offer.description)
                    putString(ARG_OFFER_REQS,     offer.requirements)
                    putString(ARG_COMPANY_ID,     companyId)
                    putString(ARG_COMPANY_NAME,   companyName)
                    putString(ARG_SUPERVISOR_UID, supervisorUid)
                    putString(ARG_APP_STATUS,     applicationStatus)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInternshipOfferDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        val offerId       = arguments?.getString(ARG_OFFER_ID)       ?: ""
        val offerTitle    = arguments?.getString(ARG_OFFER_TITLE)    ?: ""
        val department    = arguments?.getString(ARG_OFFER_DEPT)     ?: ""
        val duration      = arguments?.getString(ARG_OFFER_DURATION) ?: ""
        val seats         = arguments?.getInt(ARG_OFFER_SEATS, 0)    ?: 0
        val description   = arguments?.getString(ARG_OFFER_DESC)     ?: ""
        val requirements  = arguments?.getString(ARG_OFFER_REQS)     ?: ""
        val companyId     = arguments?.getString(ARG_COMPANY_ID)     ?: ""
        val companyName   = arguments?.getString(ARG_COMPANY_NAME)   ?: ""
        val supervisorUid = arguments?.getString(ARG_SUPERVISOR_UID) ?: ""
        val appStatus     = arguments?.getString(ARG_APP_STATUS)     ?: ""

        binding.tvOfferDetailsTitle.text = offerTitle.ifBlank { "Internship Offer" }
        binding.tvOfferDetailsCompany.text = companyName
        // Seed with the snapshot, then resolve the true owner-based name asynchronously.
        liveCompanyName = companyName
        resolveOwnerCompanyName(supervisorUid, companyName)

        if (department.isNotBlank()) {
            binding.tvOfferDetailsDept.text = "Dept: $department"
            binding.tvOfferDetailsDept.visibility = View.VISIBLE
        }
        if (duration.isNotBlank()) {
            binding.tvOfferDetailsDuration.text = "$duration wks"
            binding.tvOfferDetailsDuration.visibility = View.VISIBLE
        }
        if (seats > 0) {
            binding.tvOfferDetailsSeats.text = "$seats seats"
            binding.tvOfferDetailsSeats.visibility = View.VISIBLE
        }

        // Company name is a secondary action → open the Company Profile.
        if (companyId.isNotBlank()) {
            binding.tvOfferDetailsCompany.setOnClickListener {
                (requireActivity() as? StudentDashBoard)
                    ?.openDetail(CompanyProfileFragment.newInstance(companyId))
            }
        }

        binding.tvOfferDetailsDescription.text =
            description.ifBlank { "No description provided." }

        if (requirements.isNotBlank()) {
            binding.tvOfferDetailsRequirements.text = requirements
            binding.cardOfferRequirements.visibility = View.VISIBLE
        }

        // Seed with whatever Explore already knew, then render + kick a live refresh so the button
        // reflects the true latest state (fixes: after Back from Apply, button was still "Apply Now").
        currentAppStatus = appStatus
        renderApplicationState(companyId, companyName, supervisorUid, offerId, offerTitle)

        binding.btnOfferDetailsBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Renders the Apply button + "already applied" card from [currentAppStatus]. */
    private fun renderApplicationState(
        companyId: String,
        companyName: String,
        supervisorUid: String,
        offerId: String,
        offerTitle: String
    ) {
        when (currentAppStatus) {
            // In-progress statuses block a duplicate application.
            "PENDING" -> showAlreadyApplied(
                statusText = "Status: PENDING — Under Review",
                statusColor = "#D97706",
                message = "Your application is under review. You will be notified when the company responds."
            )
            "ACCEPTED" -> showAlreadyApplied(
                statusText = "Status: ACCEPTED",
                statusColor = "#16A34A",
                message = "Congratulations! Your application has been accepted by this company."
            )
            // A previously REJECTED student may apply again: show the note AND keep the Apply button.
            "REJECTED" -> {
                binding.layoutOfferAlreadyApplied.visibility = View.VISIBLE
                binding.tvOfferDetailsAppStatus.text = "Status: REJECTED"
                binding.tvOfferDetailsAppStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.tvOfferDetailsAppMessage.text =
                    "Your previous application was not accepted. You can apply again below."
                enableApply(companyId, companyName, supervisorUid, offerId, offerTitle, "Apply Again")
            }
            else -> {
                // No prior application OR only CANCELLED — regular Apply Now.
                binding.layoutOfferAlreadyApplied.visibility = View.GONE
                enableApply(companyId, companyName, supervisorUid, offerId, offerTitle, "Apply Now")
            }
        }
    }

    /**
     * Re-fetches the student's latest application for THIS offer and re-renders the button state.
     * Prevents the stale-snapshot bug where pressing Back from a fresh Apply left "Apply Now" showing
     * even though the app was PENDING in Firestore.
     */
    private fun refreshApplicationStatus(
        companyId: String,
        companyName: String,
        supervisorUid: String,
        offerId: String,
        offerTitle: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                // getStudentApplications is sorted newest-first — the first hit for this offer/company
                // is the latest.
                val latest = if (offerId.isNotBlank()) {
                    apps.firstOrNull { it.offerId == offerId }
                } else {
                    apps.firstOrNull { it.companyId == companyId }
                }
                val newStatus = latest?.status ?: ""
                if (newStatus != currentAppStatus) {
                    currentAppStatus = newStatus
                    renderApplicationState(companyId, companyName, supervisorUid, offerId, offerTitle)
                }
            },
            onFailure = { /* best-effort — keep whatever we're showing */ }
        )
    }

    private fun enableApply(
        companyId: String,
        companyName: String,
        supervisorUid: String,
        offerId: String,
        offerTitle: String,
        label: String
    ) {
        binding.btnOfferDetailsApply.visibility = View.VISIBLE
        binding.btnOfferDetailsApply.text = label
        binding.btnOfferDetailsApply.setOnClickListener {
            // Prefer the live-resolved owner company name over the possibly-stale snapshot.
            val nameForApply = liveCompanyName.ifBlank { companyName }
            val frag = ApplyInternshipFragment.newInstance(
                companyId = companyId,
                companyName = nameForApply,
                supervisorUid = supervisorUid,
                offerId = offerId,
                offerTitle = offerTitle
            )
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, frag)
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Overrides the header company name with the CURRENT owner-based value. The offer's
     * `companyName` field is a snapshot taken when the offer was published; if the supervisor's
     * company has been renamed since (or the field was stale to begin with), we prefer the live
     * value from users/verifications/companies for display + downstream Apply.
     */
    private fun resolveOwnerCompanyName(supervisorUid: String, snapshot: String) {
        if (supervisorUid.isBlank()) return
        authRepo.resolveCompanyNameForSupervisor(
            supervisorUid = supervisorUid,
            fallbackSnapshot = snapshot,
            onResult = { resolved ->
                if (_binding == null) return@resolveCompanyNameForSupervisor
                val display = resolved.ifBlank { snapshot.ifBlank { "Company unavailable" } }
                liveCompanyName = display
                binding.tvOfferDetailsCompany.text = display
            }
        )
    }

    private fun showAlreadyApplied(statusText: String, statusColor: String, message: String) {
        binding.layoutOfferAlreadyApplied.visibility = View.VISIBLE
        binding.tvOfferDetailsAppStatus.text = statusText
        binding.tvOfferDetailsAppStatus.setTextColor(Color.parseColor(statusColor))
        binding.tvOfferDetailsAppMessage.text = message
        binding.btnOfferDetailsApply.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
        // Re-check application status every time we come back to this screen (fixes the "Apply Now
        // still showing after Back from Apply" bug). Argument-snapshot rendering already happened
        // in onViewCreated; this just corrects it when Firestore has moved on.
        val args = arguments ?: return
        refreshApplicationStatus(
            companyId = args.getString(ARG_COMPANY_ID) ?: "",
            companyName = args.getString(ARG_COMPANY_NAME) ?: "",
            supervisorUid = args.getString(ARG_SUPERVISOR_UID) ?: "",
            offerId = args.getString(ARG_OFFER_ID) ?: "",
            offerTitle = args.getString(ARG_OFFER_TITLE) ?: ""
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Do NOT restore the shared header/nav here. The destination fragment's
        // onResume restores chrome. Re-showing it from onDestroyView lands on top of
        // the next detail screen (animation delays this call), which is exactly what
        // stacked the old "Companies" header behind "Offer Details".
        _binding = null
    }
}
