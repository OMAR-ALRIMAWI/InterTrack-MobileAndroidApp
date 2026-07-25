package com.example.intertrack.fragments.admin

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreUniversityChangeRequest
import com.example.intertrack.databinding.FragmentAdminUniversityRequestsBinding
import com.example.intertrack.databinding.ItemUniversityChangeRequestBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Admin review list for university change requests. Loading → Content / Empty / Error, pull-to-refresh.
 * Approve applies the change to the user's document (repository); Reject stores a reason. Both notify
 * the requesting user.
 */
class AdminUniversityRequestsFragment : Fragment() {

    private var _binding: FragmentAdminUniversityRequestsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUniversityRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUcr.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshUcr.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshUcr.setOnRefreshListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun showLoading() {
        binding.progressUcr.visibility =
            if (binding.swipeRefreshUcr.isRefreshing) View.GONE else View.VISIBLE
        binding.rvUcr.visibility = View.GONE
        binding.tvUcrEmpty.visibility = View.GONE
        binding.tvUcrError.visibility = View.GONE
        binding.rvUcr.adapter = null
    }

    private fun load() {
        showLoading()
        authRepo.getUniversityChangeRequests(
            onSuccess = { all ->
                if (_binding == null) return@getUniversityChangeRequests
                binding.progressUcr.visibility = View.GONE
                binding.swipeRefreshUcr.isRefreshing = false
                val pendingCount = all.count { it.status == "PENDING" }
                binding.tvUcrCount.text = "University change requests ($pendingCount pending)"
                if (all.isEmpty()) {
                    binding.tvUcrEmpty.visibility = View.VISIBLE
                    binding.rvUcr.visibility = View.GONE
                } else {
                    binding.tvUcrEmpty.visibility = View.GONE
                    binding.rvUcr.visibility = View.VISIBLE
                    binding.rvUcr.adapter = UniversityRequestAdapter(
                        all,
                        onApprove = { req -> confirmApprove(req) },
                        onReject = { req -> confirmReject(req) }
                    )
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@getUniversityChangeRequests
                binding.progressUcr.visibility = View.GONE
                binding.swipeRefreshUcr.isRefreshing = false
                binding.rvUcr.visibility = View.GONE
                binding.tvUcrError.visibility = View.VISIBLE
                binding.tvUcrError.text = "Could not load requests. Please try again."
            }
        )
    }

    private fun confirmApprove(req: FirestoreUniversityChangeRequest) {
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Approve University Change")
            .setMessage("Approve ${req.userName}'s change to ${req.requestedUniversity}? Their university will be updated.")
            .setPositiveButton("Approve") { _, _ ->
                authRepo.approveUniversityChangeRequest(
                    request = req,
                    adminUid = adminUid,
                    onSuccess = {
                        if (_binding == null) return@approveUniversityChangeRequest
                        Toast.makeText(requireContext(), "Request approved.", Toast.LENGTH_SHORT).show()
                        load()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@approveUniversityChangeRequest
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmReject(req: FirestoreUniversityChangeRequest) {
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val input = EditText(requireContext()).apply {
            hint = "Rejection reason (optional)"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reject University Change")
            .setMessage("Reject ${req.userName}'s request? Their university stays unchanged.")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text?.toString()?.trim()
                    ?.ifBlank { "Request did not meet requirements." } ?: "Request did not meet requirements."
                authRepo.rejectUniversityChangeRequest(
                    requestId = req.requestId,
                    userUid = req.userUid,
                    adminUid = adminUid,
                    reason = reason,
                    onSuccess = {
                        if (_binding == null) return@rejectUniversityChangeRequest
                        Toast.makeText(requireContext(), "Request rejected.", Toast.LENGTH_SHORT).show()
                        load()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@rejectUniversityChangeRequest
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class UniversityRequestAdapter(
    private val items: List<FirestoreUniversityChangeRequest>,
    private val onApprove: (FirestoreUniversityChangeRequest) -> Unit,
    private val onReject: (FirestoreUniversityChangeRequest) -> Unit
) : RecyclerView.Adapter<UniversityRequestAdapter.VH>() {

    inner class VH(val binding: ItemUniversityChangeRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemUniversityChangeRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        with(holder.binding) {
            tvUcrName.text = r.userName.ifBlank { "User" }
            val roleLabel = when (r.userRole) {
                "STUDENT" -> "Student"
                "INSTRUCTOR" -> "Instructor"
                else -> r.userRole
            }
            tvUcrRoleEmail.text = listOf(roleLabel, r.userEmail).filter { it.isNotBlank() }.joinToString(" · ")
            tvUcrChange.text = "${r.currentUniversity.ifBlank { "Not set" }} → ${r.requestedUniversity}"
            tvUcrReason.text = "Reason: ${r.reason.ifBlank { "—" }}"
            tvUcrProof.text = if (r.proofFileName.isNotBlank())
                "Document: ${r.proofFileName} · Document record only - file preview unavailable"
            else
                "No document provided."

            when (r.status) {
                "APPROVED" -> {
                    tvUcrStatus.text = "APPROVED"
                    tvUcrStatus.setTextColor(Color.parseColor("#166534"))
                    tvUcrStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_approved)
                    ucrActions.visibility = View.GONE
                }
                "REJECTED" -> {
                    tvUcrStatus.text = "REJECTED"
                    tvUcrStatus.setTextColor(Color.parseColor("#DC2626"))
                    tvUcrStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_rejected)
                    ucrActions.visibility = View.GONE
                }
                else -> {
                    tvUcrStatus.text = "PENDING"
                    tvUcrStatus.setTextColor(Color.parseColor("#92400E"))
                    tvUcrStatus.setBackgroundResource(com.example.intertrack.R.drawable.bg_status_pending)
                    ucrActions.visibility = View.VISIBLE
                    btnUcrApprove.setOnClickListener { onApprove(r) }
                    btnUcrReject.setOnClickListener { onReject(r) }
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
