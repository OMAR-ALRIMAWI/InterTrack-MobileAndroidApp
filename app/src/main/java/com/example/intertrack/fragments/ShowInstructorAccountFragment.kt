package com.example.intertrack.fragments

import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.databinding.FragmentShowInstructorAccountBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.parcelize.Parcelize

class ShowInstructorAccountFragment : Fragment() {

    private var _binding: FragmentShowInstructorAccountBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowInstructorAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        val instructor = arguments?.getParcelable<InstructorParcel>(KEY_INSTRUCTOR)

        instructor?.let {
            binding.tvInstProfileInitials.text = it.initials
            binding.tvInstProfileName.text = it.name
            binding.tvInstProfileDept.text = it.department
            binding.tvInstProfileUniversity.text = it.university
            binding.tvInstEmail.text = it.email
            binding.tvInstOffice.text = "Office: ${it.office}"
            binding.tvInstBio.text = it.bio

            try {
                val avatarParent = binding.tvInstProfileInitials.parent
                if (avatarParent is android.widget.FrameLayout) {
                    avatarParent.background?.setTint(Color.parseColor(it.accentColor))
                }
            } catch (_: Exception) {}
        }

        binding.btnInstructorProfileBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val studentUid = FirebaseAuth.getInstance().currentUser?.uid
        val instructorUid = instructor?.uid

        if (studentUid != null && !instructorUid.isNullOrBlank()) {
            checkExistingRequest(studentUid, instructorUid)
        }

        binding.btnFollowInstructor.setOnClickListener {
            if (studentUid != null && !instructorUid.isNullOrBlank()) {
                sendInstructorRequest(studentUid, instructorUid, instructor)
            }
        }

        binding.btnMessageInstructor.setOnClickListener {
            if (studentUid != null && !instructorUid.isNullOrBlank()) {
                startConversationWithInstructor(studentUid, instructorUid, instructor?.name ?: "Instructor")
            }
        }
    }

    private fun startConversationWithInstructor(studentUid: String, instructorUid: String, instructorName: String) {
        val studentName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
        authRepo.getOrCreateConversation(
            uidA = studentUid,
            nameA = studentName,
            uidB = instructorUid,
            nameB = instructorName,
            onSuccess = { conversationId ->
                if (_binding == null) return@getOrCreateConversation
                val chat = ChatFragment.newInstance(conversationId, instructorUid, instructorName)
                (requireActivity() as? StudentDashBoard)?.openDetail(chat)
            },
            onFailure = { /* fail silently */ }
        )
    }

    private fun checkExistingRequest(studentUid: String, instructorUid: String) {
        authRepo.getRequestByStudentAndInstructor(
            studentUid = studentUid,
            instructorUid = instructorUid,
            onSuccess = { existing ->
                if (_binding == null) return@getRequestByStudentAndInstructor
                if (existing != null) {
                    when (existing.status) {
                        "PENDING" -> {
                            binding.btnFollowInstructor.text = "Request Pending"
                            binding.btnFollowInstructor.isEnabled = false
                        }
                        "ACCEPTED" -> {
                            binding.btnFollowInstructor.text = "Supervisor Accepted"
                            binding.btnFollowInstructor.isEnabled = false
                        }
                        "REJECTED" -> {
                            binding.btnFollowInstructor.text = "Request Declined"
                            binding.btnFollowInstructor.isEnabled = false
                        }
                        else -> { /* enable button for re-request */ }
                    }
                }
            },
            onFailure = { /* fail silently — button remains active */ }
        )
    }

    private fun sendInstructorRequest(
        studentUid: String,
        instructorUid: String,
        instructor: InstructorParcel?
    ) {
        binding.btnFollowInstructor.isEnabled = false
        binding.btnFollowInstructor.text = "Sending…"

        authRepo.fetchUserDocument(
            uid = studentUid,
            onSuccess = { student ->
                if (_binding == null) return@fetchUserDocument
                val request = FirestoreInstructorRequest(
                    studentUid = studentUid,
                    studentName = student.fullName,
                    studentEmail = student.email,
                    studentUniversity = student.university ?: "",
                    studentMajor = student.major ?: "",
                    studentGpa = "",
                    studentCompanyName = "",
                    instructorUid = instructorUid,
                    instructorName = instructor?.name ?: ""
                )
                authRepo.submitInstructorRequest(
                    request = request,
                    onSuccess = { _ ->
                        if (_binding == null) return@submitInstructorRequest
                        binding.btnFollowInstructor.text = "Request Pending"
                        binding.btnFollowInstructor.isEnabled = false
                        Toast.makeText(requireContext(),
                            "Supervisor request sent successfully.", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@submitInstructorRequest
                        binding.btnFollowInstructor.isEnabled = true
                        binding.btnFollowInstructor.text = "Request as Supervisor"
                        Toast.makeText(requireContext(),
                            "Request failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onFailure = { msg ->
                if (_binding == null) return@fetchUserDocument
                binding.btnFollowInstructor.isEnabled = true
                binding.btnFollowInstructor.text = "Request as Supervisor"
                Toast.makeText(requireContext(),
                    "Could not load your profile: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
        val instructor = arguments?.getParcelable<InstructorParcel>(KEY_INSTRUCTOR)
        val studentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val instructorUid = instructor?.uid
        if (studentUid != null && !instructorUid.isNullOrBlank()) {
            checkExistingRequest(studentUid, instructorUid)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Header is re-shown by the destination fragment's onResume (avoids duplicated header).
        _binding = null
    }

    companion object {
        private const val KEY_INSTRUCTOR = "instructor"

        fun newInstance(instructor: Instructor): ShowInstructorAccountFragment {
            return ShowInstructorAccountFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_INSTRUCTOR, InstructorParcel.from(instructor))
                }
            }
        }
    }
}

@Parcelize
data class InstructorParcel(
    val uid: String,
    val initials: String,
    val name: String,
    val department: String,
    val university: String,
    val email: String,
    val office: String,
    val bio: String,
    val accentColor: String
) : Parcelable {

    companion object {
        fun from(instructor: Instructor): InstructorParcel {
            return InstructorParcel(
                uid = instructor.id,
                initials = instructor.initials,
                name = instructor.name,
                department = instructor.department,
                university = instructor.university,
                email = instructor.email,
                office = instructor.office,
                bio = instructor.bio,
                accentColor = instructor.accentColor
            )
        }
    }
}
