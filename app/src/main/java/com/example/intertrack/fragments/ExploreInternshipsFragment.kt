package com.example.intertrack.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.ExploreOffer
import com.example.intertrack.databinding.FragmentExploreInternshipsBinding
import com.example.intertrack.databinding.ItemExploreInternshipOfferBinding
import com.google.firebase.auth.FirebaseAuth

class ExploreInternshipsFragment : Fragment() {

    private var _binding: FragmentExploreInternshipsBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    private var allOffers = listOf<ExploreOffer>()
    private val filteredOffers = mutableListOf<ExploreOffer>()
    private var appliedStatusByOfferId = mapOf<String, String>()
    private lateinit var adapter: ExploreOfferAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreInternshipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExploreOfferAdapter(
            items = filteredOffers,
            appliedStatus = { appliedStatusByOfferId[it] },
            onOfferClick = { openOfferDetails(it) },
            onCompanyClick = { openCompanyProfile(it) }
        )
        binding.rvExploreOffers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExploreOffers.adapter = adapter

        binding.etExploreSearch.addTextChangedListener { text ->
            applyFilter(text?.toString()?.trim()?.lowercase() ?: "")
        }

        binding.swipeRefreshExplore.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshExplore.setOnRefreshListener { loadOffers(silent = true) }
        binding.tvExploreError.setOnClickListener { loadOffers() }

        loadOffers()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "ExploreInternshipsFragment -> Loading")
        binding.exploreProgress.visibility = View.VISIBLE
        binding.rvExploreOffers.visibility = View.GONE
        binding.tvExploreEmpty.visibility = View.GONE
        binding.tvExploreError.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "ExploreInternshipsFragment -> Content")
        binding.exploreProgress.visibility = View.GONE
        binding.rvExploreOffers.visibility = View.VISIBLE
        binding.tvExploreEmpty.visibility = View.GONE
        binding.tvExploreError.visibility = View.GONE
        binding.swipeRefreshExplore.isRefreshing = false
    }

    private fun showEmptyState(message: String) {
        Log.d("INTERTRACK_STATE", "ExploreInternshipsFragment -> Empty")
        binding.exploreProgress.visibility = View.GONE
        binding.rvExploreOffers.visibility = View.GONE
        binding.tvExploreEmpty.visibility = View.VISIBLE
        binding.tvExploreEmpty.text = message
        binding.tvExploreError.visibility = View.GONE
        binding.swipeRefreshExplore.isRefreshing = false
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "ExploreInternshipsFragment -> Error: $message")
        binding.exploreProgress.visibility = View.GONE
        binding.rvExploreOffers.visibility = View.GONE
        binding.tvExploreEmpty.visibility = View.GONE
        binding.tvExploreError.visibility = View.VISIBLE
        binding.tvExploreError.text = message
        binding.swipeRefreshExplore.isRefreshing = false
    }

    private fun loadOffers(silent: Boolean = false) {
        if (!silent) showLoadingState()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            appliedStatusByOfferId = emptyMap()
            fetchOffers()
            return
        }
        
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                appliedStatusByOfferId = apps
                    .filter { it.status != "CANCELLED" && it.offerId.isNotBlank() }
                    .associate { it.offerId to it.status }
                fetchOffers()
            },
            onFailure = { _ ->
                if (_binding == null) return@getStudentApplications
                appliedStatusByOfferId = emptyMap()
                fetchOffers()
            }
        )
    }

    private fun fetchOffers() {
        authRepo.fetchOpenOffersWithCompanies(
            onSuccess = { offers ->
                if (_binding == null) return@fetchOpenOffersWithCompanies
                allOffers = offers
                applyFilter(binding.etExploreSearch.text?.toString()?.trim()?.lowercase() ?: "")
                
                if (offers.isEmpty()) {
                    showEmptyState("No open internships are available right now.")
                } else {
                    if (filteredOffers.isEmpty()) {
                        showEmptyState("No internships match your search.")
                    } else {
                        showContentState()
                    }
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@fetchOpenOffersWithCompanies
                showErrorState(msg ?: "Failed to load internships")
            }
        )
    }

    private fun applyFilter(query: String) {
        filteredOffers.clear()
        filteredOffers.addAll(
            if (query.isEmpty()) allOffers
            else allOffers.filter { eo ->
                eo.offer.title.lowercase().contains(query) ||
                    eo.offer.companyName.lowercase().contains(query) ||
                    eo.offer.department.lowercase().contains(query) ||
                    (eo.company?.city?.lowercase()?.contains(query) ?: false) ||
                    (eo.company?.industry?.lowercase()?.contains(query) ?: false)
            }
        )
        adapter.notifyDataSetChanged()

        if (allOffers.isNotEmpty()) {
            if (filteredOffers.isEmpty()) {
                showEmptyState("No internships match \"$query\"")
            } else {
                showContentState()
            }
        }
    }

    private fun openOfferDetails(eo: ExploreOffer) {
        // Pass the FRESH companies-doc name (matches what the card just showed) instead of the
        // possibly-stale offer.companyName snapshot; Offer Details still runs its own supervisor
        // resolver, but this avoids a flash of "TechCorp Turkey" while that runs.
        val freshName = eo.company?.name?.takeIf { it.isNotBlank() } ?: eo.offer.companyName
        val frag = InternshipOfferDetailsFragment.newInstance(
            offer = eo.offer,
            companyId = eo.offer.companyId,
            companyName = freshName,
            supervisorUid = eo.offer.supervisorUid,
            applicationStatus = appliedStatusByOfferId[eo.offer.offerId] ?: ""
        )
        (requireActivity() as? StudentDashBoard)?.openDetail(frag)
    }

    private fun openCompanyProfile(eo: ExploreOffer) {
        if (eo.offer.companyId.isBlank()) return
        (requireActivity() as? StudentDashBoard)
            ?.openDetail(CompanyProfileFragment.newInstance(eo.offer.companyId))
    }

    override fun onResume() {
        super.onResume()
        loadOffers(silent = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ExploreOfferAdapter(
    private val items: List<ExploreOffer>,
    private val appliedStatus: (offerId: String) -> String?,
    private val onOfferClick: (ExploreOffer) -> Unit,
    private val onCompanyClick: (ExploreOffer) -> Unit
) : RecyclerView.Adapter<ExploreOfferAdapter.VH>() {

    inner class VH(val binding: ItemExploreInternshipOfferBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemExploreInternshipOfferBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val eo = items[position]
        val offer = eo.offer
        val company = eo.company
        with(holder.binding) {
            // Prefer the FRESH companies-doc name over the offer's possibly-stale snapshot so a
            // renamed company shows correctly (offer.companyName is only a snapshot at publish time).
            val companyName = company?.name?.takeIf { it.isNotBlank() }
                ?: offer.companyName.ifBlank { "Company" }
            tvExploreTitle.text = offer.title.ifBlank { "Internship Offer" }
            tvExploreCompany.text = companyName
            tvExploreInitial.text = companyName.firstOrNull()?.toString()?.uppercase() ?: "?"
            tvExploreDesc.text = offer.description.ifBlank { "No description provided." }

            bindMeta(chipExploreDept, if (offer.department.isNotBlank()) "Dept: ${offer.department}" else "")
            bindMeta(chipExploreLocation, company?.city?.takeIf { it.isNotBlank() }?.let { "$it, Turkey" } ?: "")
            bindMeta(chipExploreDuration, if (offer.duration.isNotBlank()) "${offer.duration} wks" else "")
            bindMeta(chipExploreSeats, if (offer.seats > 0) "${offer.seats} seats" else "")

            val status = appliedStatus(offer.offerId)
            if (status.isNullOrBlank()) {
                tvExploreApplied.visibility = View.GONE
            } else {
                tvExploreApplied.visibility = View.VISIBLE
                when (status) {
                    "ACCEPTED" -> {
                        tvExploreApplied.text = "Accepted"
                        tvExploreApplied.setTextColor(Color.parseColor("#16A34A"))
                        tvExploreApplied.setBackgroundResource(R.drawable.bg_verified_pill)
                    }
                    "REJECTED" -> {
                        tvExploreApplied.text = "Rejected"
                        tvExploreApplied.setTextColor(Color.parseColor("#DC2626"))
                        tvExploreApplied.setBackgroundResource(R.drawable.bg_status_rejected)
                    }
                    else -> {
                        tvExploreApplied.text = "Pending"
                        tvExploreApplied.setTextColor(Color.parseColor("#D97706"))
                        tvExploreApplied.setBackgroundResource(R.drawable.bg_pending_pill)
                    }
                }
            }

            cardExploreOffer.setOnClickListener { onOfferClick(eo) }
            tvExploreCompany.setOnClickListener { onCompanyClick(eo) }
        }
    }

    private fun bindMeta(view: android.widget.TextView, value: String) {
        if (value.isBlank()) {
            view.visibility = View.GONE
        } else {
            view.text = value
            view.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = items.size
}
