package com.example.intertrack.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.LoginActivity
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.User
import com.example.intertrack.databinding.FragmentProfileStudentBinding
import com.google.firebase.auth.FirebaseAuth

class ProfileStudent : Fragment() {

    private var _binding: FragmentProfileStudentBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    private var ctxCompanyId: String = ""
    private var ctxSupervisorUid: String = ""
    private var ctxSupervisorName: String = ""
    private var ctxInstructorUid: String = ""
    private var ctxInstructorName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfile()

        val dash = requireActivity() as? StudentDashBoard

        binding.btnEditProfile.setOnClickListener { dash?.openDetail(EditProfileFragment()) }
        binding.cardCompletedInternships.setOnClickListener {
            dash?.openDetail(StudentCompletedInternshipsFragment())
        }
        binding.rowChangeUniversity.setOnClickListener {
            dash?.openDetail(ChangeUniversityFragment())
        }
        binding.cardUniversity.setOnClickListener { dash?.openDetail(VerifyStudentFragment()) }
        binding.rowAccountSettings.setOnClickListener { dash?.openDetail(AccountSettingsFragment()) }
        binding.rowPrivacy.setOnClickListener { dash?.openDetail(PrivacySecurityFragment()) }
        binding.rowHelp.setOnClickListener { dash?.openDetail(HelpSupportFragment()) }

        binding.btnLogout.setOnClickListener {
            AppSessionCache.clear()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "ProfileStudent -> Loading")
        binding.progressProfileStudent.visibility = View.VISIBLE
        binding.profileStudentContent.visibility = View.GONE
        binding.tvProfileStudentError.visibility = View.GONE

        binding.tvStudentName.text = ""
        binding.tvStudentEmail.text = ""
        binding.tvStudentUniversity.text = ""
        binding.tvStudentCompany.text = ""
        binding.tvInstructorsCount.text = "0"
        binding.tvSupervisorName.text = ""
        binding.tvCompletedInternshipsCount.text = "0"
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "ProfileStudent -> Content")
        binding.progressProfileStudent.visibility = View.GONE
        binding.profileStudentContent.visibility = View.VISIBLE
        binding.tvProfileStudentError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "ProfileStudent -> Error: $message")
        binding.progressProfileStudent.visibility = View.GONE
        binding.profileStudentContent.visibility = View.GONE
        binding.tvProfileStudentError.visibility = View.VISIBLE
        binding.tvProfileStudentError.text = message
    }

    private fun refreshChangeUniversityStatus(uid: String) {
        authRepo.getPendingUniversityChangeRequest(
            userUid = uid,
            onSuccess = { pending ->
                if (_binding == null) return@getPendingUniversityChangeRequest
                binding.tvChangeUniversitySubtitle.text = if (pending != null)
                    "Pending: ${pending.requestedUniversity}"
                else
                    "Request a university change"
            },
            onFailure = { }
        )
    }

    private fun bindProfile(user: User) {
        binding.tvStudentInitials.text = user.initials()
        binding.tvStudentName.text = user.fullName.ifBlank { "Student" }
        binding.tvStudentRole.text = "Student"
        binding.tvStudentEmail.text = user.email
        binding.tvStudentUniversity.text = user.university?.takeIf { it.isNotBlank() }
            ?: com.example.intertrack.data.model.UniversityUtil.displayNameForKey(user.universityKey)
                .takeIf { it.isNotBlank() }
            ?: "Not set"
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        showLoadingState()

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                AppSessionCache.currentUser = user
                bindProfile(user)
                refreshChangeUniversityStatus(uid)
                
                authRepo.getStudentInstructorRequests(
                    studentUid = uid,
                    onSuccess = { requests ->
                        if (_binding == null) return@getStudentInstructorRequests
                        val count = requests.count { it.status == "ACCEPTED" }
                        if (ctxInstructorName.isBlank()) {
                            binding.tvInstructorsCount.text = count.toString()
                        }
                    },
                    onFailure = {}
                )

                authRepo.getCompletedInternshipCount(
                    studentUid = uid,
                    scopeRole = "STUDENT",
                    scopeUid = uid,
                    scopeCompanyId = "",
                    onResult = { completed ->
                        if (_binding == null) return@getCompletedInternshipCount
                        binding.tvCompletedInternshipsCount.text = completed.toString()
                    }
                )

                authRepo.getStudentActiveConnection(
                    studentUid = uid,
                    onSuccess = { conn ->
                        if (_binding == null) return@getStudentActiveConnection
                        if (conn != null) {
                            bindConnectionCards(conn)
                            showContentState()
                        } else {
                            bindFromApplications(uid)
                        }
                    },
                    onFailure = {
                        if (_binding == null) return@getStudentActiveConnection
                        bindFromApplications(uid)
                    }
                )
            },
            onFailure = { message ->
                if (_binding == null) return@fetchUserDocument
                showErrorState(message)
            }
        )
    }

    private fun bindFromApplications(uid: String) {
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                val accepted = apps.firstOrNull { it.status == "ACCEPTED" }
                if (accepted != null) {
                    ctxCompanyId = accepted.companyId
                    ctxSupervisorUid = accepted.supervisorUid
                    ctxSupervisorName = ""
                    ctxInstructorUid = accepted.assignedInstructorUid
                    ctxInstructorName = accepted.assignedInstructorName

                    binding.tvStudentCompany.text = accepted.companyName.ifBlank { "No active internship" }
                    binding.tvInstructorsCount.text = accepted.assignedInstructorName.ifBlank { "Waiting for instructor" }
                    binding.tvSupervisorName.text =
                        if (accepted.supervisorUid.isNotBlank()) "Company Supervisor assigned" else "Not assigned"

                    wireCardActions(waitingHub = true, connectionId = accepted.applicationId)
                } else {
                    showNoInternshipCards()
                }
                showContentState()
            },
            onFailure = {
                if (_binding == null) return@getStudentApplications
                showNoInternshipCards()
                showContentState()
            }
        )
    }

    private fun bindConnectionCards(conn: FirestoreInternshipConnection) {
        ctxCompanyId = conn.companyId
        ctxSupervisorUid = conn.supervisorUid
        ctxSupervisorName = conn.supervisorName
        ctxInstructorUid = conn.instructorUid
        ctxInstructorName = conn.instructorName

        binding.tvStudentCompany.text = conn.companyName.ifBlank { "No active internship" }

        binding.tvInstructorsCount.text = when {
            conn.instructorName.isNotBlank() -> conn.instructorName
            conn.status == "WAITING_INSTRUCTOR_CONNECTION" -> "Waiting for instructor"
            else -> "No instructor assigned"
        }

        binding.tvSupervisorName.text = when {
            conn.supervisorName.isNotBlank() -> conn.supervisorName
            conn.supervisorUid.isNotBlank() -> "Company Supervisor assigned"
            else -> "Not assigned"
        }

        wireCardActions(waitingHub = conn.status != "ACTIVE", connectionId = conn.connectionId)
    }

    private fun wireCardActions(waitingHub: Boolean, connectionId: String) {
        val dash = requireActivity() as? StudentDashBoard

        binding.cardCurrentCompany.setOnClickListener {
            if (ctxCompanyId.isNotBlank()) {
                dash?.openDetail(CompanyProfileFragment.newInstance(ctxCompanyId))
            }
        }

        binding.cardInstructors.setOnClickListener {
            dash?.openDetail(StudentInstructorsFragment())
        }

        binding.cardSupervisor.setOnClickListener {
            if (ctxSupervisorUid.isNotBlank()) {
                val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                val myName = AppSessionCache.currentUser?.fullName ?: ""
                authRepo.getOrCreateConversation(
                    uidA = myUid, nameA = myName,
                    uidB = ctxSupervisorUid, nameB = ctxSupervisorName.ifBlank { "Company Supervisor" },
                    onSuccess = { convId ->
                        if (_binding == null) return@getOrCreateConversation
                        dash?.openDetail(ChatFragment.newInstance(convId, ctxSupervisorUid,
                            ctxSupervisorName.ifBlank { "Company Supervisor" }))
                    },
                    onFailure = {}
                )
            } else {
                dash?.openDetail(InternshipProgressHubFragment.newInstance(connectionId, "STUDENT"))
            }
        }
    }

    private fun showNoInternshipCards() {
        ctxCompanyId = ""; ctxSupervisorUid = ""; ctxSupervisorName = ""
        ctxInstructorUid = ""; ctxInstructorName = ""

        binding.tvStudentCompany.text = "No active internship"
        binding.tvSupervisorName.text = "Add Instructor"

        val dash = requireActivity() as? StudentDashBoard
        binding.cardSupervisor.setOnClickListener { dash?.openDetail(AddInstructorFragment()) }
        binding.cardInstructors.setOnClickListener { dash?.openDetail(StudentInstructorsFragment()) }
        binding.cardCurrentCompany.setOnClickListener {
            dash?.openDetail(StudentInternshipsFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Profile")
        }
        loadProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
