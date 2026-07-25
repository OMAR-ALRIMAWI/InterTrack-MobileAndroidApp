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
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.isValidActive
import com.example.intertrack.databinding.FragmentCompanyHomeBinding
import com.example.intertrack.databinding.ItemActiveInternBinding
import com.google.firebase.auth.FirebaseAuth

class CompanyHomeFragment : Fragment() {

    private var _binding: FragmentCompanyHomeBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvActiveInterns.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActiveInterns.isNestedScrollingEnabled = false
        loadDashboard()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "CompanyHomeFragment -> Loading")
        binding.progressCompHomeMain.visibility = View.VISIBLE
        binding.companyHomeContent.visibility = View.GONE
        binding.tvCompHomeError.visibility = View.GONE

        binding.tvCompHomeActiveCount.text = "0"
        binding.tvCompHomeWaitingCount.text = "0"
        binding.tvCompHomePendingApps.text = "0"
        binding.tvCompHomeOpenOffers.text = "0"
        binding.tvCompHomeReportsCount.text = "0"
        binding.rvActiveInterns.adapter = null
        binding.tvNoActiveInterns.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "CompanyHomeFragment -> Content")
        binding.progressCompHomeMain.visibility = View.GONE
        binding.companyHomeContent.visibility = View.VISIBLE
        binding.tvCompHomeError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "CompanyHomeFragment -> Empty")
        showContentState()
        binding.rvActiveInterns.visibility = View.GONE
        binding.tvNoActiveInterns.visibility = View.VISIBLE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "CompanyHomeFragment -> Error: $message")
        binding.progressCompHomeMain.visibility = View.GONE
        binding.companyHomeContent.visibility = View.GONE
        binding.tvCompHomeError.visibility = View.VISIBLE
        binding.tvCompHomeError.text = message
    }

    private fun loadDashboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val company = AppSessionCache.currentCompany
        val companyId = company?.companyId ?: ""

        showLoadingState()

        val displayName = company?.name
            ?: AppSessionCache.currentUser?.fullName?.split(" ")?.first()
            ?: "Supervisor"
        (requireActivity() as? CompanyDashBoard)?.updateHeader("Dashboard", "Hello, $displayName")

        if (companyId.isBlank()) {
            showEmptyState()
            return
        }

        authRepo.getCompanyConnections(
            companyId = companyId,
            onSuccess = { connections ->
                if (_binding == null) return@getCompanyConnections
                val validActive = connections.filter { it.isValidActive() }
                val invalidActive = connections.filter { !it.isValidActive() }
                invalidActive.forEach { conn ->
                    authRepo.repairInvalidActiveConnection(conn.connectionId) { repaired ->
                        if (repaired && _binding != null) refreshWaitingCount(companyId)
                    }
                }
                
                if (validActive.isNotEmpty()) {
                    showConnections(validActive)
                } else {
                    loadFallbackFromApplications(uid, companyId)
                }
                showContentState()
            },
            onFailure = { msg ->
                if (_binding == null) return@getCompanyConnections
                showErrorState(msg ?: "Could not load dashboard.")
            }
        )

        refreshWaitingCount(companyId)

        authRepo.getCompanyApplications(
            supervisorUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getCompanyApplications
                val pending = apps.count { it.status == "PENDING" }
                binding.tvCompHomePendingApps.text = pending.toString()
            },
            onFailure = {}
        )

        authRepo.getCompanyActiveOffers(
            supervisorUid = uid,
            onSuccess = { offers ->
                if (_binding == null) return@getCompanyActiveOffers
                binding.tvCompHomeOpenOffers.text = offers.count { it.status == "OPEN" }.toString()
            },
            onFailure = {}
        )

        authRepo.getCompanyReports(
            companyId = companyId,
            onSuccess = { reports ->
                if (_binding == null) return@getCompanyReports
                binding.tvCompHomeReportsCount.text = reports.size.toString()
            },
            onFailure = {}
        )
    }

    private fun refreshWaitingCount(companyId: String) {
        authRepo.getCompanyWaitingConnections(
            companyId = companyId,
            onSuccess = { waiting ->
                if (_binding == null) return@getCompanyWaitingConnections
                binding.tvCompHomeWaitingCount.text = waiting.size.toString()
            },
            onFailure = {}
        )
    }

    private fun loadFallbackFromApplications(supervisorUid: String, companyId: String) {
        authRepo.getCompanyApplications(
            supervisorUid = supervisorUid,
            onSuccess = { apps ->
                if (_binding == null) return@getCompanyApplications
                val accepted = apps.filter { it.status == "ACCEPTED" && it.companyId == companyId }
                binding.tvCompHomeActiveCount.text = "0"
                if (accepted.isEmpty()) {
                    showEmptyState()
                } else {
                    accepted.forEach { app ->
                        val conn = appToConnection(app, supervisorUid)
                        authRepo.createInternshipConnection(conn, onSuccess = {}, onFailure = {})
                    }
                    showEmptyState()
                }
            },
            onFailure = {
                if (_binding == null) return@getCompanyApplications
                binding.tvCompHomeActiveCount.text = "0"
                showEmptyState()
            }
        )
    }

    private fun appToConnection(app: FirestoreInternshipApplication, supervisorUid: String): FirestoreInternshipConnection {
        val supervisorName = AppSessionCache.currentUser?.fullName ?: AppSessionCache.currentCompany?.name ?: ""
        return FirestoreInternshipConnection(
            connectionId = app.applicationId,
            applicationId = app.applicationId,
            studentUid = app.studentUid,
            studentName = app.studentName,
            companyId = app.companyId,
            companyName = app.companyName,
            supervisorUid = supervisorUid,
            supervisorName = supervisorName,
            internshipId = app.offerId,
            internshipTitle = app.offerTitle.ifBlank { "Internship" },
            instructorUid = app.assignedInstructorUid,
            instructorName = app.assignedInstructorName,
            status = "WAITING_INSTRUCTOR_CONNECTION"
        )
    }

    private fun showConnections(connections: List<FirestoreInternshipConnection>) {
        binding.tvCompHomeActiveCount.text = connections.size.toString()
        binding.tvNoActiveInterns.visibility = View.GONE
        binding.rvActiveInterns.visibility = View.VISIBLE
        binding.rvActiveInterns.adapter = ActiveInternsAdapter(
            items = connections,
            onProgressHub = { conn ->
                (requireActivity() as? CompanyDashBoard)?.openDetail(
                    InternshipProgressHubFragment.newInstance(conn.connectionId, "COMPANY")
                )
            },
            onProgressChat = { conn -> openProgressChat(conn) },
            onEndInternship = { conn -> confirmEndInternship(conn) }
        )
    }

    private fun openProgressChat(conn: FirestoreInternshipConnection) {
        val supervisorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val supervisorName = AppSessionCache.currentUser?.fullName ?: ""
        authRepo.getOrCreateProgressConversation(
            connectionId = conn.connectionId,
            studentUid = conn.studentUid,
            studentName = conn.studentName,
            supervisorUid = supervisorUid,
            supervisorName = supervisorName,
            instructorUid = conn.instructorUid,
            instructorName = conn.instructorName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateProgressConversation
                val title = "${conn.internshipTitle.ifBlank { "Internship" }} - Internship Chat"
                val subtitle = "${conn.studentName} • ${conn.companyName}"
                val chat = ChatFragment.newInstance(conversationId, "GROUP", title, subtitle)
                (requireActivity() as? CompanyDashBoard)?.openDetail(chat)
            },
            onFailure = { msg ->
                if (_binding == null) return@getOrCreateProgressConversation
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmEndInternship(conn: FirestoreInternshipConnection) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Reason for ending this internship"
            setPadding(48, 32, 48, 0)
            minLines = 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("End Internship")
            .setMessage("End ${conn.studentName}'s internship? The student and instructor will be notified. This cannot be undone.")
            .setView(input)
            .setPositiveButton("End Internship") { _, _ ->
                val reason = input.text?.toString()?.trim() ?: ""
                if (reason.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a reason.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val companyId = conn.companyId.ifBlank { AppSessionCache.currentCompany?.companyId ?: "" }
                val companyName = conn.companyName.ifBlank { AppSessionCache.currentCompany?.name ?: "" }
                authRepo.endInternshipConnection(
                    connectionId = conn.connectionId,
                    studentUid = conn.studentUid,
                    instructorUid = conn.instructorUid,
                    reason = reason,
                    companyId = companyId,
                    companyName = companyName,
                    onSuccess = {
                        if (_binding == null) return@endInternshipConnection
                        Toast.makeText(requireContext(), "Internship ended. Student and instructor notified.", Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@endInternshipConnection
                        Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val company = AppSessionCache.currentCompany
        val displayName = company?.name
            ?: AppSessionCache.currentUser?.fullName?.split(" ")?.first()
            ?: "Supervisor"
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Dashboard", "Hello, $displayName")
        }
        loadDashboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ActiveInternsAdapter(
    private val items: List<FirestoreInternshipConnection>,
    private val onProgressHub: (FirestoreInternshipConnection) -> Unit,
    private val onProgressChat: (FirestoreInternshipConnection) -> Unit,
    private val onEndInternship: (FirestoreInternshipConnection) -> Unit
) : RecyclerView.Adapter<ActiveInternsAdapter.VH>() {

    inner class VH(val binding: ItemActiveInternBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemActiveInternBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conn = items[position]
        with(holder.binding) {
            val nameParts = conn.studentName.trim().split(" ").filter { it.isNotEmpty() }
            tvInternInitials.text = when {
                nameParts.size >= 2 -> "${nameParts[0].first()}${nameParts[1].first()}".uppercase()
                nameParts.size == 1 -> nameParts[0].take(2).uppercase()
                else -> "?"
            }
            tvInternName.text = conn.studentName.ifBlank { "Unknown Student" }
            tvInternTitle.text = conn.internshipTitle.ifBlank { "Intern" }

            if (conn.instructorName.isNotBlank()) {
                tvInternInstructor.text = "Instructor: ${conn.instructorName}"
                tvInternInstructor.visibility = View.VISIBLE
            } else {
                tvInternInstructor.visibility = View.GONE
            }

            btnInternProgressHub.setOnClickListener { onProgressHub(conn) }
            btnInternProgressChat.setOnClickListener { onProgressChat(conn) }
            btnInternEndInternship.setOnClickListener { onEndInternship(conn) }
        }
    }

    override fun getItemCount() = items.size
}
