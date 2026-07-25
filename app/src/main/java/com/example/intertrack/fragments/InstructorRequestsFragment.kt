package com.example.intertrack.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentInstructorRequestsBinding
import com.example.intertrack.databinding.ItemPendingConnectionBinding
import com.google.firebase.auth.FirebaseAuth

class InstructorRequestsFragment : Fragment() {

    private var _binding: FragmentInstructorRequestsBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructorRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPendingRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAcceptedStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPendingConnections.layoutManager = LinearLayoutManager(requireContext())

        binding.swipeRefreshInstructorRequests.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshInstructorRequests.setOnRefreshListener { loadRequests() }

        loadRequests()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "InstructorRequestsFragment -> Loading")
        // Skip the section spinners during a pull-to-refresh (the swipe spinner already shows).
        val loadingVis = if (binding.swipeRefreshInstructorRequests.isRefreshing) View.GONE else View.VISIBLE
        binding.progressRequests.visibility = loadingVis
        binding.progressPendingConnections.visibility = loadingVis

        binding.rvPendingRequests.visibility = View.GONE
        binding.rvAcceptedStudents.visibility = View.GONE
        binding.rvPendingConnections.visibility = View.GONE
        
        binding.tvPendingEmpty.visibility = View.GONE
        binding.tvNoAcceptedStudents.visibility = View.GONE
        binding.tvNoPendingConnections.visibility = View.GONE
        binding.tvRequestsError.visibility = View.GONE
        binding.tvAcceptedSectionHeader.visibility = View.GONE
    }

    private fun loadRequests() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        showLoadingState()

        authRepo.getInstructorRequests(
            instructorUid = uid,
            onSuccess = { requests ->
                if (_binding == null) return@getInstructorRequests
                binding.progressRequests.visibility = View.GONE

                val pending = requests.filter { it.status == "PENDING" }
                val accepted = requests.filter { it.status == "ACCEPTED" }

                setupPendingSection(pending, uid)
                setupAcceptedSection(accepted)
            },
            onFailure = { msg ->
                if (_binding == null) return@getInstructorRequests
                binding.progressRequests.visibility = View.GONE
                binding.tvRequestsError.text = msg ?: "Could not load requests."
                binding.tvRequestsError.visibility = View.VISIBLE
            }
        )

        loadPendingConnections(uid)
    }

    private fun loadPendingConnections(instructorUid: String) {
        authRepo.getInstructorPendingConnections(
            instructorUid = instructorUid,
            onSuccess = { connections ->
                if (_binding == null) return@getInstructorPendingConnections
                binding.progressPendingConnections.visibility = View.GONE
                binding.swipeRefreshInstructorRequests.isRefreshing = false
                setupPendingConnectionsSection(connections)
            },
            onFailure = { msg ->
                if (_binding == null) return@getInstructorPendingConnections
                binding.progressPendingConnections.visibility = View.GONE
                binding.swipeRefreshInstructorRequests.isRefreshing = false
                setupPendingConnectionsSection(emptyList())
            }
        )
    }

    private fun setupPendingConnectionsSection(connections: List<FirestoreInternshipConnection>) {
        if (connections.isEmpty()) {
            binding.tvNoPendingConnections.visibility = View.VISIBLE
            binding.rvPendingConnections.visibility = View.GONE
        } else {
            binding.tvNoPendingConnections.visibility = View.GONE
            binding.rvPendingConnections.visibility = View.VISIBLE
            binding.rvPendingConnections.adapter = PendingConnectionAdapter(
                items = connections,
                onConnect = { conn -> showConnectDialog(conn) },
                onOpenHub = { conn -> openInstructorHub(conn) }
            )
        }
    }

    private fun openInstructorHub(conn: FirestoreInternshipConnection) {
        (requireActivity() as? InstructorDashBoard)
            ?.openDetail(InternshipProgressHubFragment.newInstance(conn.connectionId, "INSTRUCTOR"))
    }

    private fun showConnectDialog(conn: FirestoreInternshipConnection) {
        val periodNote = if (conn.hasPeriod()) {
            "The company has set the internship period. "
        } else {
            "Note: the company has not set the internship period yet. "
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Connect Internship")
            .setMessage("${periodNote}Connect ${conn.studentName} with ${conn.companyName.ifBlank { "the company" }} now?")
            .setPositiveButton("Connect") { _, _ ->
                val instructorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val instructorName = AppSessionCache.currentUser?.fullName ?: ""
                authRepo.connectInternship(
                    connectionId = conn.connectionId,
                    instructorUid = instructorUid,
                    instructorName = instructorName,
                    studentUid = conn.studentUid,
                    studentName = conn.studentName,
                    supervisorUid = conn.supervisorUid,
                    supervisorName = conn.supervisorName,
                    onSuccess = {
                        if (_binding == null) return@connectInternship
                        Toast.makeText(requireContext(),
                            "${conn.studentName}'s internship is now active.",
                            Toast.LENGTH_SHORT).show()
                        loadRequests()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@connectInternship
                        Toast.makeText(requireContext(),
                            "Could not activate the internship. Please try again.",
                            Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupPendingSection(
        pending: List<FirestoreInstructorRequest>,
        instructorUid: String
    ) {
        if (pending.isEmpty()) {
            binding.tvPendingEmpty.visibility = View.VISIBLE
            binding.rvPendingRequests.visibility = View.GONE
        } else {
            binding.tvPendingEmpty.visibility = View.GONE
            binding.rvPendingRequests.visibility = View.VISIBLE
            val adapter = InstructorRequestAdapter(
                items = pending,
                onAccept = { req -> showAcceptDialog(req, instructorUid) },
                onDecline = { req -> showDeclineDialog(req, instructorUid) }
            )
            binding.rvPendingRequests.adapter = adapter
        }
    }

    private fun setupAcceptedSection(accepted: List<FirestoreInstructorRequest>) {
        binding.tvAcceptedSectionHeader.visibility = View.VISIBLE

        if (accepted.isEmpty()) {
            binding.tvNoAcceptedStudents.visibility = View.VISIBLE
            binding.rvAcceptedStudents.visibility = View.GONE
        } else {
            binding.tvNoAcceptedStudents.visibility = View.GONE
            binding.rvAcceptedStudents.visibility = View.VISIBLE
            val adapter = InstructorRequestAdapter(
                items = accepted,
                onAccept = { /* no action */ },
                onDecline = { /* no action */ },
                onMessage = { request -> startConversationWithStudent(request) }
            )
            binding.rvAcceptedStudents.adapter = adapter
        }
    }

    private fun startConversationWithStudent(request: FirestoreInstructorRequest) {
        val instructorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val instructorName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""

        authRepo.getOrCreateConversation(
            uidA = instructorUid,
            nameA = instructorName,
            uidB = request.studentUid,
            nameB = request.studentName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateConversation
                val chat = ChatFragment.newInstance(conversationId, request.studentUid, request.studentName)
                (requireActivity() as? InstructorDashBoard)?.openDetail(chat)
            },
            onFailure = { }
        )
    }

    private fun showAcceptDialog(
        request: FirestoreInstructorRequest,
        instructorUid: String
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("Accept Request")
            .setMessage("Accept ${request.studentName} as your supervised student?")
            .setPositiveButton("Accept") { _, _ ->
                authRepo.fetchUserDocument(
                    uid = instructorUid,
                    onSuccess = { instructor ->
                        if (_binding == null) return@fetchUserDocument
                        authRepo.acceptInstructorRequest(
                            requestId = request.requestId,
                            studentUid = request.studentUid,
                            instructorUid = instructorUid,
                            instructorName = instructor.fullName,
                            reviewerUid = instructorUid,
                            onSuccess = {
                                if (_binding == null) return@acceptInstructorRequest
                                Toast.makeText(requireContext(),
                                    "${request.studentName} added to your supervision list.",
                                    Toast.LENGTH_SHORT).show()
                                loadRequests()
                            },
                            onFailure = { msg ->
                                if (_binding == null) return@acceptInstructorRequest
                                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@fetchUserDocument
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeclineDialog(
        request: FirestoreInstructorRequest,
        instructorUid: String
    ) {
        val input = EditText(requireContext()).apply {
            hint = "Reason for declining (optional)"
            setPadding(48, 32, 48, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Decline Request")
            .setMessage("Decline request from ${request.studentName}?")
            .setView(input)
            .setPositiveButton("Decline") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                authRepo.rejectInstructorRequest(
                    requestId = request.requestId,
                    reviewerUid = instructorUid,
                    reason = reason,
                    onSuccess = {
                        if (_binding == null) return@rejectInstructorRequest
                        Toast.makeText(requireContext(),
                            "Request from ${request.studentName} declined.",
                            Toast.LENGTH_SHORT).show()
                        loadRequests()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@rejectInstructorRequest
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? InstructorDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Requests")
        }
        loadRequests()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PendingConnectionAdapter(
    private val items: List<FirestoreInternshipConnection>,
    private val onConnect: (FirestoreInternshipConnection) -> Unit,
    private val onOpenHub: (FirestoreInternshipConnection) -> Unit
) : RecyclerView.Adapter<PendingConnectionAdapter.VH>() {

    inner class VH(val binding: ItemPendingConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPendingConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conn = items[position]
        with(holder.binding) {
            val nameParts = conn.studentName.trim().split(" ").filter { it.isNotEmpty() }
            tvPendConnInitials.text = when {
                nameParts.size >= 2 -> "${nameParts[0].first()}${nameParts[1].first()}".uppercase()
                nameParts.size == 1 -> nameParts[0].take(2).uppercase()
                else -> "?"
            }
            tvPendConnStudentName.text = conn.studentName.ifBlank { "Student" }
            tvPendConnInternshipTitle.text = conn.internshipTitle.ifBlank { "Internship" }
            tvPendConnCompanyName.text = conn.companyName.ifBlank { "—" }
            btnPendConnOpenHub.setOnClickListener { onOpenHub(conn) }
            btnConnectInternship.setOnClickListener { onConnect(conn) }
        }
    }

    override fun getItemCount() = items.size
}
