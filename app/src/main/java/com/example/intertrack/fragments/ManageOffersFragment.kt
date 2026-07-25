package com.example.intertrack.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.databinding.FragmentManageOffersBinding
import com.example.intertrack.databinding.ItemManageOfferRowBinding
import com.google.firebase.auth.FirebaseAuth

class ManageOffersFragment : Fragment() {

    private var _binding: FragmentManageOffersBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageOffersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvManageOffers.layoutManager = LinearLayoutManager(requireContext())
        binding.btnManageBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.swipeRefreshManageOffers.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshManageOffers.setOnRefreshListener { loadOffers() }
        loadOffers()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "ManageOffersFragment -> Loading")
        // Skip the center spinner during a pull-to-refresh (the swipe spinner already shows).
        binding.progressManageOffers.visibility =
            if (binding.swipeRefreshManageOffers.isRefreshing) View.GONE else View.VISIBLE
        binding.rvManageOffers.visibility = View.GONE
        binding.tvManageOffersEmpty.visibility = View.GONE
        binding.tvManageOffersError.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "ManageOffersFragment -> Content")
        binding.progressManageOffers.visibility = View.GONE
        binding.swipeRefreshManageOffers.isRefreshing = false
        binding.rvManageOffers.visibility = View.VISIBLE
        binding.tvManageOffersEmpty.visibility = View.GONE
        binding.tvManageOffersError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "ManageOffersFragment -> Empty")
        binding.progressManageOffers.visibility = View.GONE
        binding.swipeRefreshManageOffers.isRefreshing = false
        binding.rvManageOffers.visibility = View.GONE
        binding.tvManageOffersEmpty.visibility = View.VISIBLE
        binding.tvManageOffersError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "ManageOffersFragment -> Error: $message")
        binding.progressManageOffers.visibility = View.GONE
        binding.swipeRefreshManageOffers.isRefreshing = false
        binding.rvManageOffers.visibility = View.GONE
        binding.tvManageOffersEmpty.visibility = View.GONE
        binding.tvManageOffersError.visibility = View.VISIBLE
        binding.tvManageOffersError.text = message
    }

    private fun loadOffers() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        showLoadingState()

        authRepo.getCompanyActiveOffers(
            supervisorUid = uid,
            onSuccess = { offers ->
                if (_binding == null) return@getCompanyActiveOffers
                val visible = offers.filter { it.status != "DELETED" }
                if (visible.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.rvManageOffers.adapter = ManageOfferAdapter(
                        items = visible,
                        onEdit = { offer -> openEditOffer(offer) },
                        onDelete = { offer -> confirmDelete(offer) },
                        onReopen = { offer -> confirmReopen(offer) }
                    )
                    showContentState()
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@getCompanyActiveOffers
                showErrorState(msg ?: "Could not load offers.")
            }
        )
    }

    private fun openEditOffer(offer: FirestoreInternshipOffer) {
        val frag = EditOfferFragment.newInstance(
            offerId = offer.offerId,
            title = offer.title,
            department = offer.department,
            description = offer.description,
            requirements = offer.requirements,
            duration = offer.duration,
            seats = offer.seats
        )
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.compFragmentContainer, frag)
            .addToBackStack(null)
            .commit()
    }

    private fun confirmReopen(offer: FirestoreInternshipOffer) {
        AlertDialog.Builder(requireContext())
            .setTitle("Reopen Offer")
            .setMessage("Reopen \"${offer.title}\"? Students will be able to see and apply to it again.")
            .setPositiveButton("Reopen") { _, _ ->
                authRepo.reopenInternshipOffer(
                    offerId = offer.offerId,
                    onSuccess = {
                        if (_binding == null) return@reopenInternshipOffer
                        Toast.makeText(requireContext(), "Offer reopened.", Toast.LENGTH_SHORT).show()
                        loadOffers()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@reopenInternshipOffer
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(offer: FirestoreInternshipOffer) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Offer")
            .setMessage("Delete \"${offer.title}\"? This will close the offer and hide it from students.")
            .setPositiveButton("Delete") { _, _ ->
                authRepo.deleteInternshipOffer(
                    offerId = offer.offerId,
                    onSuccess = {
                        if (_binding == null) return@deleteInternshipOffer
                        Toast.makeText(requireContext(), "Offer deleted.", Toast.LENGTH_SHORT).show()
                        loadOffers()
                    },
                    onFailure = { _ ->
                        if (_binding == null) return@deleteInternshipOffer
                        Toast.makeText(requireContext(),
                            "Could not delete offer. Please try again.", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Manage Offers")
        }
        loadOffers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ManageOfferAdapter(
    private val items: List<FirestoreInternshipOffer>,
    private val onEdit: (FirestoreInternshipOffer) -> Unit,
    private val onDelete: (FirestoreInternshipOffer) -> Unit,
    private val onReopen: (FirestoreInternshipOffer) -> Unit
) : RecyclerView.Adapter<ManageOfferAdapter.VH>() {

    inner class VH(val binding: ItemManageOfferRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemManageOfferRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val offer = items[position]
        with(holder.binding) {
            tvManageOfferTitle.text = offer.title.ifBlank { "Untitled Offer" }

            val meta = buildString {
                if (offer.department.isNotBlank()) append(offer.department)
                if (offer.department.isNotBlank() && offer.duration.isNotBlank()) append(" · ")
                if (offer.duration.isNotBlank()) append("${offer.duration} wks")
                if (offer.seats > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${offer.seats} seat${if (offer.seats != 1) "s" else ""}")
                }
            }
            tvManageOfferMeta.text = meta

            when (offer.status) {
                "OPEN" -> {
                    tvManageOfferStatus.text = "Open"
                    tvManageOfferStatus.setTextColor(android.graphics.Color.parseColor("#166534"))
                    tvManageOfferStatus.background = androidx.core.content.ContextCompat.getDrawable(
                        holder.itemView.context, com.example.intertrack.R.drawable.bg_status_approved)
                }
                "CLOSED" -> {
                    tvManageOfferStatus.text = "Closed"
                    tvManageOfferStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    tvManageOfferStatus.background = androidx.core.content.ContextCompat.getDrawable(
                        holder.itemView.context, com.example.intertrack.R.drawable.bg_status_rejected)
                }
                else -> {
                    tvManageOfferStatus.text = offer.status
                    tvManageOfferStatus.setTextColor(android.graphics.Color.parseColor("#6B7280"))
                    tvManageOfferStatus.background = null
                }
            }

            // Reopen only for CLOSED offers (never DELETED — those stay hidden from this list).
            btnManageOfferReopen.visibility =
                if (offer.status == "CLOSED") android.view.View.VISIBLE else android.view.View.GONE
            btnManageOfferReopen.setOnClickListener { onReopen(offer) }

            btnManageOfferEdit.setOnClickListener { onEdit(offer) }
            btnManageOfferDelete.setOnClickListener { onDelete(offer) }
        }
    }

    override fun getItemCount() = items.size
}
