package com.example.intertrack.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.data.model.User
import com.example.intertrack.databinding.FragmentAddInstructorBinding
import com.google.firebase.auth.FirebaseAuth

class AddInstructorFragment : Fragment() {

    private var _binding: FragmentAddInstructorBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private lateinit var adapter: InstructorAdapter

    private var allInstructors: List<User>? = null
    private var excludedUids: Set<String>? = null
    private var acceptedInstructorUid: String? = null

    // The signed-in student's normalized university key. null = not loaded yet; "" = missing/blank.
    private var studentUniversityKey: String? = null
    private var studentUniversityLoaded: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddInstructorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        binding.btnAddInstructorBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = InstructorAdapter(emptyList()) { instructor ->
            val fragment = ShowInstructorAccountFragment.newInstance(instructor)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.rvInstructors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInstructors.adapter = adapter

        binding.etInstructorSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadInstructors()
    }

    private fun loadInstructors() {
        binding.instructorsProgress.visibility = View.VISIBLE
        binding.tvInstructorsEmpty.visibility = View.GONE
        binding.tvInstructorsError.visibility = View.GONE
        binding.rvInstructors.visibility = View.GONE
        binding.cardYourInstructor.visibility = View.GONE

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("FindInstructor", "loadInstructors — callerUid=$uid")

        authRepo.fetchActiveInstructors(
            onSuccess = { users ->
                if (_binding == null) return@fetchActiveInstructors
                Log.d("FindInstructor", "fetchActiveInstructors returned ${users.size} results")
                allInstructors = users
                tryRenderInstructors()
            },
            onFailure = { msg ->
                if (_binding == null) return@fetchActiveInstructors
                Log.e("FindInstructor", "fetchActiveInstructors failed: $msg")
                binding.instructorsProgress.visibility = View.GONE
                val userMsg = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Could not load instructors. Please try again."
                } else {
                    "Could not load instructors. Please check your connection and try again."
                }
                binding.tvInstructorsError.text = userMsg
                binding.tvInstructorsError.visibility = View.VISIBLE
            }
        )

        if (uid != null) {
            // Read the student's own (admin-approved) university from their user document, so the
            // list can be limited to instructors from the same university.
            authRepo.fetchUserDocument(
                uid = uid,
                onSuccess = { user ->
                    if (_binding == null) return@fetchUserDocument
                    studentUniversityKey = universityKeyOf(user.universityKey, user.university)
                    studentUniversityLoaded = true
                    tryRenderInstructors()
                },
                onFailure = {
                    if (_binding == null) return@fetchUserDocument
                    // On error treat as missing (safe: never falls back to showing all universities).
                    studentUniversityKey = ""
                    studentUniversityLoaded = true
                    tryRenderInstructors()
                }
            )

            authRepo.getStudentInstructorRequests(
                studentUid = uid,
                onSuccess = { requests ->
                    if (_binding == null) return@getStudentInstructorRequests
                    val accepted = requests.find { it.status == "ACCEPTED" }
                    acceptedInstructorUid = accepted?.instructorUid
                    excludedUids = requests
                        .filter { it.status == "PENDING" || it.status == "ACCEPTED" }
                        .map { it.instructorUid }
                        .toSet()
                    tryRenderInstructors()
                },
                onFailure = { _ ->
                    if (_binding == null) return@getStudentInstructorRequests
                    excludedUids = emptySet()
                    tryRenderInstructors()
                }
            )
        } else {
            excludedUids = emptySet()
            studentUniversityKey = ""
            studentUniversityLoaded = true
        }
    }

    private fun tryRenderInstructors() {
        val users = allInstructors ?: return
        val excluded = excludedUids ?: return
        if (!studentUniversityLoaded) return

        binding.instructorsProgress.visibility = View.GONE

        // Guard: without a known student university we must not show instructors from other schools.
        val studentKey = studentUniversityKey ?: ""
        if (studentKey.isBlank()) {
            binding.cardYourInstructor.visibility = View.GONE
            binding.rvInstructors.visibility = View.GONE
            binding.etInstructorSearch.isEnabled = false
            binding.tvInstructorsEmpty.text =
                "Your university information is missing. Please contact admin or update your verification."
            binding.tvInstructorsEmpty.visibility = View.VISIBLE
            return
        }
        binding.etInstructorSearch.isEnabled = true

        // Show "Your Instructor" card if student has an accepted instructor
        val acceptedUid = acceptedInstructorUid
        if (acceptedUid != null) {
            val instructor = users.find { it.uid == acceptedUid }
            if (instructor != null) {
                binding.tvYourInstructorName.text = instructor.fullName.ifBlank { "Instructor" }
                binding.tvYourInstructorDetails.text = listOfNotNull(
                    instructor.department?.takeIf { it.isNotBlank() },
                    instructor.university?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                binding.cardYourInstructor.visibility = View.VISIBLE
            }
        }

        // Same-university filter: only instructors whose university matches the student's. Instructors
        // with a blank or different university are excluded.
        val filtered = users.filter {
            it.uid !in excluded &&
                universityKeyOf(it.universityKey, it.university) == studentKey
        }
        val instructors = filtered.map { userToInstructor(it) }

        adapter = InstructorAdapter(instructors) { instructor ->
            val fragment = ShowInstructorAccountFragment.newInstance(instructor)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        binding.rvInstructors.adapter = adapter

        if (instructors.isEmpty()) {
            binding.rvInstructors.visibility = View.GONE
            binding.tvInstructorsEmpty.text = "No instructors found for your university yet."
            binding.tvInstructorsEmpty.visibility = View.VISIBLE
        } else {
            binding.tvInstructorsEmpty.visibility = View.GONE
            binding.rvInstructors.visibility = View.VISIBLE
            val currentSearch = binding.etInstructorSearch.text?.toString() ?: ""
            if (currentSearch.isNotBlank()) adapter.filter(currentSearch)
        }
    }

    /** Prefer the stored universityKey; fall back to normalizing the display name for older docs. */
    private fun universityKeyOf(storedKey: String?, displayName: String?): String {
        val key = storedKey?.trim().orEmpty()
        return if (key.isNotBlank()) key else UniversityUtil.normalizeUniversityName(displayName)
    }

    private fun userToInstructor(user: User): Instructor {
        return Instructor(
            id = user.uid,
            initials = user.initials(),
            name = user.fullName.ifBlank { "Instructor" },
            department = user.department?.takeIf { it.isNotBlank() } ?: "Department not set",
            university = user.university?.takeIf { it.isNotBlank() } ?: "University not set",
            email = user.email,
            office = user.office ?: "",
            bio = user.bio ?: ""
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Header is re-shown by the destination fragment's onResume (restoring here caused a
        // duplicated header/background when moving between full-screen detail fragments).
        _binding = null
    }
}
