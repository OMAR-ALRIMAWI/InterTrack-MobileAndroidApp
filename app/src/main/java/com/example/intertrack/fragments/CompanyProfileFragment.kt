package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.databinding.FragmentCompanyProfileBinding
import com.example.intertrack.databinding.ItemCompanyOfferRowBinding
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color

class CompanyProfileFragment : Fragment() {

    private var _binding: FragmentCompanyProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var loadedCompanyName = ""
    private var loadedSupervisorUid = ""

    private var openOffers: List<FirestoreInternshipOffer>? = null
    private var studentApplications: List<FirestoreInternshipApplication>? = null

    companion object {
        private const val ARG_COMPANY_ID = "company_id"

        fun newInstance(companyId: String): CompanyProfileFragment {
            return CompanyProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMPANY_ID, companyId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        startLoad()

        val companyId = arguments?.getString(ARG_COMPANY_ID)
        if (!companyId.isNullOrBlank()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            loadStudentApplications(uid, companyId)
            loadCompany(companyId)
        } else {
            binding.tvCompanyProfileName.text = "Company not found"
        }

        binding.btnDmCompany.setOnClickListener {
            startConversationWithCompany()
        }

        binding.btnCompanyProfileBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun startLoad() {
        openOffers = null
        studentApplications = null
        binding.tvCompanyProfileInitial.text = "?"
        binding.tvCompanyProfileName.text = ""
        binding.tvCompanyProfileIndustry.text = ""
        binding.tvCompanyProfileSize.text = ""
        binding.tvCompanyAbout.text = ""
        binding.progressCompanyOffers.visibility = View.VISIBLE
        binding.llCompanyOffersContainer.visibility = View.GONE
        binding.tvCompanyOffersEmpty.visibility = View.GONE
    }

    private fun loadStudentApplications(uid: String?, companyId: String) {
        if (uid == null) {
            studentApplications = emptyList()
            tryRenderOffers()
            return
        }
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                studentApplications = apps.filter { it.companyId == companyId }
                tryRenderOffers()
            },
            onFailure = { _ ->
                if (_binding == null) return@getStudentApplications
                studentApplications = emptyList()
                tryRenderOffers()
            }
        )
    }

    private fun loadCompany(companyId: String) {
        authRepo.fetchCompany(
            companyId = companyId,
            onSuccess = { company ->
                if (_binding == null) return@fetchCompany
                if (company == null) {
                    binding.tvCompanyProfileName.text = "Company not found"
                    binding.tvCompanyAbout.text = "This company profile could not be loaded."
                    openOffers = emptyList()
                    tryRenderOffers()
                    return@fetchCompany
                }
                loadedCompanyName = company.name
                loadedSupervisorUid = company.supervisorUid
                binding.tvCompanyProfileInitial.text =
                    company.name.firstOrNull()?.toString()?.uppercase() ?: "?"
                binding.tvCompanyProfileName.text = company.name.ifBlank { "Company" }
                val industryCity = buildString {
                    if (company.industry.isNotBlank()) append(company.industry)
                    if (company.industry.isNotBlank() && company.city.isNotBlank()) append(" · ")
                    if (company.city.isNotBlank()) append("${company.city}, Turkey")
                }
                binding.tvCompanyProfileIndustry.text = industryCity.ifBlank { "—" }
                binding.tvCompanyProfileSize.text = company.size.ifBlank { "" }
                binding.tvCompanyAbout.text = company.description.ifBlank { "No description provided." }

                loadOffersWithFallback(companyId, company.supervisorUid)
            },
            onFailure = { _ ->
                if (_binding == null) return@fetchCompany
                binding.tvCompanyProfileName.text = "Could not load company"
                binding.tvCompanyAbout.text = "Please check your connection and try again."
                openOffers = emptyList()
                tryRenderOffers()
            }
        )
    }

    private fun loadOffersWithFallback(companyId: String, supervisorUid: String) {
        Log.d("CompanyProfile", "Loading offers — companyId=$companyId supervisorUid=$supervisorUid")
        authRepo.getOffersByCompany(
            companyId = companyId,
            onSuccess = { offers ->
                if (_binding == null) return@getOffersByCompany
                Log.d("CompanyProfile", "getOffersByCompany returned ${offers.size} OPEN offers for companyId=$companyId")
                if (offers.isNotEmpty() || supervisorUid.isBlank()) {
                    openOffers = offers
                    tryRenderOffers()
                } else {
                    Log.d("CompanyProfile", "Falling back to supervisorUid query: $supervisorUid")
                    authRepo.getCompanyActiveOffers(
                        supervisorUid = supervisorUid,
                        onSuccess = { fallback ->
                            if (_binding == null) return@getCompanyActiveOffers
                            val open = fallback.filter { it.status == "OPEN" }
                            Log.d("CompanyProfile", "supervisorUid fallback returned ${open.size} OPEN offers")
                            openOffers = open
                            tryRenderOffers()
                        },
                        onFailure = { err ->
                            if (_binding == null) return@getCompanyActiveOffers
                            Log.e("CompanyProfile", "supervisorUid fallback failed: $err")
                            openOffers = emptyList()
                            tryRenderOffers()
                        }
                    )
                }
            },
            onFailure = { err ->
                if (_binding == null) return@getOffersByCompany
                Log.e("CompanyProfile", "getOffersByCompany failed for companyId=$companyId: $err")
                openOffers = emptyList()
                tryRenderOffers()
            }
        )
    }

    private fun tryRenderOffers() {
        val offers = openOffers ?: return
        val apps = studentApplications ?: return

        binding.progressCompanyOffers.visibility = View.GONE
        binding.llCompanyOffersContainer.removeAllViews()

        val appliedMap = apps.filter { it.status != "CANCELLED" }
            .associateBy { it.offerId }

        when {
            offers.isEmpty() -> {
                binding.tvCompanyOffersEmpty.text = "No internship offers available right now."
                binding.tvCompanyOffersEmpty.visibility = View.VISIBLE
                binding.llCompanyOffersContainer.visibility = View.GONE
            }
            else -> {
                binding.tvCompanyOffersEmpty.visibility = View.GONE
                binding.llCompanyOffersContainer.visibility = View.VISIBLE
                inflateOfferRows(offers, appliedMap)
            }
        }
    }

    private fun inflateOfferRows(
        offers: List<FirestoreInternshipOffer>,
        appliedMap: Map<String, FirestoreInternshipApplication>
    ) {
        val companyId = arguments?.getString(ARG_COMPANY_ID) ?: return
        val container = binding.llCompanyOffersContainer
        container.removeAllViews()

        offers.forEach { offer ->
            val rowBinding = ItemCompanyOfferRowBinding.inflate(
                LayoutInflater.from(requireContext()), container, false
            )
            rowBinding.tvOfferRowTitle.text = offer.title
            val meta = buildString {
                if (offer.duration.isNotBlank()) append(offer.duration)
                if (offer.duration.isNotBlank() && offer.department.isNotBlank()) append(" · ")
                if (offer.department.isNotBlank()) append(offer.department)
                if (offer.seats > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${offer.seats} seats")
                }
            }
            rowBinding.tvOfferRowMeta.text = meta

            val app = appliedMap[offer.offerId]
            if (app != null) {
                rowBinding.tvOfferApplied.visibility = View.VISIBLE
                when (app.status) {
                    "ACCEPTED" -> {
                        rowBinding.tvOfferApplied.text = "Accepted"
                        rowBinding.tvOfferApplied.setTextColor(Color.parseColor("#16A34A"))
                        rowBinding.tvOfferApplied.setBackgroundResource(R.drawable.bg_verified_pill)
                    }
                    "REJECTED" -> {
                        rowBinding.tvOfferApplied.text = "Rejected"
                        rowBinding.tvOfferApplied.setTextColor(Color.parseColor("#DC2626"))
                        rowBinding.tvOfferApplied.setBackgroundResource(R.drawable.bg_status_rejected)
                    }
                    else -> {
                        rowBinding.tvOfferApplied.text = "Pending"
                        rowBinding.tvOfferApplied.setTextColor(Color.parseColor("#D97706"))
                        rowBinding.tvOfferApplied.setBackgroundResource(R.drawable.bg_pending_pill)
                    }
                }
            } else {
                rowBinding.tvOfferApplied.visibility = View.GONE
            }

            rowBinding.root.setOnClickListener {
                val frag = InternshipOfferDetailsFragment.newInstance(
                    offer = offer,
                    companyId = companyId,
                    companyName = loadedCompanyName,
                    supervisorUid = loadedSupervisorUid,
                    applicationStatus = app?.status ?: ""
                )
                (requireActivity() as? StudentDashBoard)?.openDetail(frag)
            }

            container.addView(rowBinding.root)
        }
    }

    private fun startConversationWithCompany() {
        if (loadedSupervisorUid.isBlank()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val studentName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
        val companyName = loadedCompanyName.ifBlank { "Company" }

        authRepo.getOrCreateConversation(
            uidA = uid,
            nameA = studentName,
            uidB = loadedSupervisorUid,
            nameB = companyName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateConversation
                val chat = ChatFragment.newInstance(conversationId, loadedSupervisorUid, companyName)
                (requireActivity() as? StudentDashBoard)?.openDetail(chat)
            },
            onFailure = { /* fail silently */ }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
        val companyId = arguments?.getString(ARG_COMPANY_ID) ?: return
        startLoad()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        loadStudentApplications(uid, companyId)
        loadCompany(companyId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Chrome is restored by the destination fragment's onResume. Re-showing it here
        // would race the next detail screen (CompanyProfile → Offer Details) and stack
        // a stale shared header on top of it.
        _binding = null
    }
}
