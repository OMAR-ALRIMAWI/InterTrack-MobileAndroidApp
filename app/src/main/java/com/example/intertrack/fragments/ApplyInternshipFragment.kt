package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.databinding.FragmentApplyInternshipBinding
import com.google.firebase.auth.FirebaseAuth

class ApplyInternshipFragment : Fragment() {

    private var _binding: FragmentApplyInternshipBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    private var companyId: String = ""
    private var companyName: String = ""
    private var supervisorUid: String = ""
    private var offerId: String = ""
    private var offerTitle: String = ""

    companion object {
        private const val ARG_COMPANY_ID    = "company_id"
        private const val ARG_COMPANY_NAME  = "company_name"
        private const val ARG_SUPERVISOR_UID = "supervisor_uid"
        private const val ARG_OFFER_ID      = "offer_id"
        private const val ARG_OFFER_TITLE   = "offer_title"

        fun newInstance(
            companyId: String,
            companyName: String,
            supervisorUid: String = "",
            offerId: String = "",
            offerTitle: String = ""
        ): ApplyInternshipFragment {
            return ApplyInternshipFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMPANY_ID, companyId)
                    putString(ARG_COMPANY_NAME, companyName)
                    putString(ARG_SUPERVISOR_UID, supervisorUid)
                    putString(ARG_OFFER_ID, offerId)
                    putString(ARG_OFFER_TITLE, offerTitle)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplyInternshipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        companyId = arguments?.getString(ARG_COMPANY_ID) ?: ""
        companyName = arguments?.getString(ARG_COMPANY_NAME) ?: ""
        supervisorUid = arguments?.getString(ARG_SUPERVISOR_UID) ?: ""
        offerId = arguments?.getString(ARG_OFFER_ID) ?: ""
        offerTitle = arguments?.getString(ARG_OFFER_TITLE) ?: ""

        binding.tvApplyCompanyName.text = if (offerTitle.isNotBlank()) "$companyName — $offerTitle" else companyName
        // Resolve the CURRENT owner-based company name — protects against a stale snapshot leaking
        // through nav args (e.g. "TechCorp Turkey" on an offer whose supervisor is Digital Cash).
        if (supervisorUid.isNotBlank()) {
            authRepo.resolveCompanyNameForSupervisor(
                supervisorUid = supervisorUid,
                fallbackSnapshot = companyName,
                onResult = { resolved ->
                    if (_binding == null) return@resolveCompanyNameForSupervisor
                    val display = resolved.ifBlank { companyName.ifBlank { "Company unavailable" } }
                    // Keep both the on-screen header and the submitted-application snapshot in sync.
                    companyName = display
                    binding.tvApplyCompanyName.text =
                        if (offerTitle.isNotBlank()) "$display — $offerTitle" else display
                }
            )
        }

        binding.btnApplyBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnCancelApplication.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSaveDraft.setOnClickListener {
            Toast.makeText(requireContext(), "Draft saving not available in Firebase mode", Toast.LENGTH_SHORT).show()
        }

        binding.btnSubmitApplication.setOnClickListener {
            if (validateForm()) {
                submitApplication()
            }
        }

        checkExistingApplication()
    }

    private fun checkExistingApplication() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (companyId.isBlank()) return

        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                // getStudentApplications is sorted newest-first, so firstOrNull is the LATEST
                // application for this offer (or company when no offer id is passed).
                val latest = if (offerId.isNotBlank()) {
                    apps.firstOrNull { it.offerId == offerId }
                } else {
                    apps.firstOrNull { it.companyId == companyId }
                }
                // Reapply policy: allow only when there is no prior application, or the latest one
                // was REJECTED or CANCELLED. PENDING/ACCEPTED (and any other in-progress status)
                // block a duplicate. Old rejected/cancelled applications are kept as history.
                val allowReapply = latest == null ||
                    latest.status == "REJECTED" || latest.status == "CANCELLED"
                if (!allowReapply) {
                    // Route the student to My Internship (which shows the pending status) so they
                    // never linger on the blocked form and can't Back-navigate to a resubmit path.
                    Toast.makeText(requireContext(),
                        "You already applied to this internship.", Toast.LENGTH_LONG).show()
                    goToMyInternship()
                } else {
                    checkOfferOpenThenPrefill(uid)
                }
            },
            onFailure = { checkOfferOpenThenPrefill(uid) }
        )
    }

    /**
     * Guards against applying to an offer that is no longer OPEN (CLOSED/DELETED) or missing. On a
     * transient read error we fall back to showing the form — the submit-time guard is the final
     * safety net. Legacy company-level applies (no offerId) skip the check.
     */
    private fun checkOfferOpenThenPrefill(uid: String) {
        if (offerId.isBlank()) { prefillFormFromUserDoc(uid); return }
        authRepo.getOfferById(
            offerId = offerId,
            onSuccess = { offer ->
                if (_binding == null) return@getOfferById
                when {
                    offer == null || offer.status == "DELETED" ->
                        showOfferUnavailableState("This internship offer is no longer available.")
                    offer.status == "CLOSED" ->
                        showOfferUnavailableState("This internship offer is closed.")
                    offer.status != "OPEN" ->
                        showOfferUnavailableState("This internship offer is not open for applications.")
                    else -> prefillFormFromUserDoc(uid)
                }
            },
            onFailure = { if (_binding != null) prefillFormFromUserDoc(uid) }
        )
    }

    private fun showOfferUnavailableState(message: String) {
        binding.tvApplicationStatus.text = "Unavailable"
        binding.layoutApplyForm.visibility = View.GONE
        binding.cardAlreadyApplied.visibility = View.VISIBLE
        binding.tvAlreadyAppliedTitle.text = "Offer Unavailable"
        binding.tvAlreadyAppliedMessage.text = message
        binding.tvAlreadyAppliedStatus.text = "Status: Closed"
        binding.tvAlreadyAppliedStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
    }

    private fun showAlreadyAppliedState(status: String) {
        binding.tvApplicationStatus.text = status
        binding.layoutApplyForm.visibility = View.GONE
        binding.cardAlreadyApplied.visibility = View.VISIBLE
        binding.tvAlreadyAppliedTitle.text = "Application Submitted"
        binding.tvAlreadyAppliedStatus.text = "Status: $status"

        binding.tvAlreadyAppliedMessage.text = when (status) {
            "PENDING" -> "Your application is under review. You will be notified once the company responds."
            "ACCEPTED" -> "Congratulations! Your application has been accepted by this company."
            "REJECTED" -> "Your application was not accepted this time. Please contact your instructor for guidance."
            else -> "You have already submitted an application to this company."
        }

        val statusColor = when (status) {
            "ACCEPTED" -> android.graphics.Color.parseColor("#16A34A")
            "REJECTED" -> android.graphics.Color.parseColor("#DC2626")
            else -> android.graphics.Color.parseColor("#0569BF")
        }
        binding.tvAlreadyAppliedStatus.setTextColor(statusColor)
    }

    private fun prefillFormFromUserDoc(uid: String) {
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                binding.etApplyStudentName.setText(user.fullName)
                binding.etApplyEmail.setText(user.email)
                binding.etApplyUniversity.setText(user.university ?: "")
                binding.etApplyMajor.setText(user.major ?: "")
                binding.etApplyYearLevel.setText(user.academicYear ?: "")
            },
            onFailure = { /* fail silently */ }
        )
    }

    private fun validateForm(): Boolean {
        val name = binding.etApplyStudentName.text?.toString()?.trim() ?: ""
        val uni = binding.etApplyUniversity.text?.toString()?.trim() ?: ""
        val major = binding.etApplyMajor.text?.toString()?.trim() ?: ""
        val email = binding.etApplyEmail.text?.toString()?.trim() ?: ""
        val yearLevel = binding.etApplyYearLevel.text?.toString()?.trim() ?: ""
        val gpa = binding.etApplyGpa.text?.toString()?.trim() ?: ""
        val phone = binding.etApplyPhone.text?.toString()?.trim() ?: ""
        val motivation = binding.etApplyMotivation.text?.toString()?.trim() ?: ""

        // Required text fields (trimmed — reject whitespace-only).
        if (name.isEmpty()) { fieldError(binding.etApplyStudentName, "Full name is required"); return false }
        if (uni.isEmpty()) { fieldError(binding.etApplyUniversity, "University is required"); return false }
        if (major.isEmpty()) { fieldError(binding.etApplyMajor, "Major is required"); return false }

        // Email — must match Android's email pattern.
        if (email.isEmpty()) { fieldError(binding.etApplyEmail, "Email is required"); return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fieldError(binding.etApplyEmail, "Enter a valid email address"); return false
        }

        // Year level — numbers only, 1..6 (optional but validated when present).
        if (yearLevel.isNotEmpty()) {
            val y = yearLevel.toIntOrNull()
            if (y == null || y !in 1..6) { fieldError(binding.etApplyYearLevel, "Year level must be 1–6"); return false }
        }

        // GPA — decimal only, 0.0..4.0 (optional but validated when present).
        if (gpa.isNotEmpty()) {
            val g = gpa.toDoubleOrNull()
            if (g == null || g < 0.0 || g > 4.0) { fieldError(binding.etApplyGpa, "GPA must be between 0.0 and 4.0"); return false }
        }

        // Phone — digits only (optional but validated when present).
        if (phone.isNotEmpty()) {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 7 || digits.length > 15) { fieldError(binding.etApplyPhone, "Enter a valid phone number"); return false }
        }

        if (motivation.isEmpty()) { fieldError(binding.etApplyMotivation, "Motivation letter is required"); return false }

        return true
    }

    private fun fieldError(field: com.google.android.material.textfield.TextInputEditText, msg: String) {
        field.error = msg
        field.requestFocus()
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun submitApplication() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Submit lock — the button is disabled immediately so a rapid double-tap can't queue a
        // second submission while the first is in flight.
        binding.btnSubmitApplication.isEnabled = false
        binding.btnSubmitApplication.text = "Submitting…"

        // Fresh dedup check right before we touch the DB. If a live PENDING/ACCEPTED application or
        // a connection already exists (e.g., the user paused the form for a while), skip the write
        // and go straight to My Internship. This is defense in depth — the repo's write-time query
        // is another layer, and the fragment's load-time check is a third.
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { apps ->
                if (_binding == null) return@getStudentApplications
                val blocking = if (offerId.isNotBlank()) {
                    apps.firstOrNull { it.offerId == offerId }
                } else {
                    apps.firstOrNull { it.companyId == companyId }
                }
                if (blocking != null && (blocking.status == "PENDING" || blocking.status == "ACCEPTED")) {
                    Toast.makeText(requireContext(),
                        "You already applied to this internship.", Toast.LENGTH_LONG).show()
                    goToMyInternship()
                    return@getStudentApplications
                }
                continueWithOfferGuard(uid)
            },
            // Read error — proceed; the repo's write-time dedup is the final gate.
            onFailure = { if (_binding != null) continueWithOfferGuard(uid) }
        )
    }

    private fun continueWithOfferGuard(uid: String) {
        // Final safety net: re-check the offer is still OPEN right before writing (it may have been
        // closed/deleted after the form was opened). Legacy company-level applies (no offerId) skip.
        if (offerId.isNotBlank()) {
            authRepo.getOfferById(
                offerId = offerId,
                onSuccess = { offer ->
                    if (_binding == null) return@getOfferById
                    if (offer == null || offer.status != "OPEN") {
                        binding.btnSubmitApplication.isEnabled = true
                        binding.btnSubmitApplication.text = "Submit Application"
                        val msg = if (offer == null || offer.status == "DELETED")
                            "This internship offer is no longer available."
                        else "This internship offer is closed."
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    } else {
                        continueSubmit(uid)
                    }
                },
                // Transient read error → don't block a legitimate submit.
                onFailure = { if (_binding != null) continueSubmit(uid) }
            )
        } else {
            continueSubmit(uid)
        }
    }

    private fun continueSubmit(uid: String) {
        // Resolve the student's assigned instructor authoritatively from their user doc.
        // A student must have an accepted instructor before applying: otherwise the
        // connection created on company acceptance would have instructorUid = "" and no
        // instructor could ever find or activate it (orphaned WAITING connection).
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                val instrUid = user.assignedInstructorUid ?: ""
                val instrName = user.assignedInstructorName ?: ""
                if (instrUid.isBlank()) {
                    binding.btnSubmitApplication.isEnabled = true
                    binding.btnSubmitApplication.text = "Submit Application"
                    Toast.makeText(requireContext(),
                        "You must have an assigned instructor before applying.",
                        Toast.LENGTH_LONG).show()
                    return@fetchUserDocument
                }
                resolveSupervisorAndSubmit(uid, instrUid, instrName)
            },
            onFailure = { _ ->
                if (_binding == null) return@fetchUserDocument
                binding.btnSubmitApplication.isEnabled = true
                binding.btnSubmitApplication.text = "Submit Application"
                Toast.makeText(requireContext(),
                    "Could not verify your instructor. Please try again.",
                    Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun resolveSupervisorAndSubmit(uid: String, instrUid: String, instrName: String) {
        if (supervisorUid.isBlank() && companyId.isNotBlank()) {
            authRepo.fetchCompany(
                companyId = companyId,
                onSuccess = { company ->
                    if (_binding == null) return@fetchCompany
                    supervisorUid = company?.supervisorUid ?: ""
                    doSubmit(uid, instrUid, instrName)
                },
                onFailure = { doSubmit(uid, instrUid, instrName) }
            )
        } else {
            doSubmit(uid, instrUid, instrName)
        }
    }

    private fun doSubmit(uid: String, instrUid: String, instrName: String) {
        val application = FirestoreInternshipApplication(
            studentUid = uid,
            studentName = binding.etApplyStudentName.text?.toString()?.trim() ?: "",
            studentEmail = binding.etApplyEmail.text?.toString()?.trim() ?: "",
            studentUniversity = binding.etApplyUniversity.text?.toString()?.trim() ?: "",
            studentMajor = binding.etApplyMajor.text?.toString()?.trim() ?: "",
            studentYearLevel = binding.etApplyYearLevel.text?.toString()?.trim() ?: "",
            studentPhone = binding.etApplyPhone.text?.toString()?.trim() ?: "",
            studentGpa = binding.etApplyGpa.text?.toString()?.trim() ?: "",
            studentSkills = binding.etApplySkills.text?.toString()?.trim() ?: "",
            motivation = binding.etApplyMotivation.text?.toString()?.trim() ?: "",
            // Official start date/duration are set by the company after acceptance (Internship
            // Period dialog), so they are intentionally not collected on the application form.
            startDate = "",
            duration = "",
            preferredDepartment = binding.etApplyPreferredDept.text?.toString()?.trim() ?: "",
            previousExperience = binding.etApplyExperience.text?.toString()?.trim() ?: "",
            portfolioLink = binding.etApplyPortfolio.text?.toString()?.trim() ?: "",
            companyId = companyId,
            companyName = companyName,
            supervisorUid = supervisorUid,
            offerId = offerId,
            offerTitle = offerTitle,
            // Carry the student's assigned instructor (resolved from their user doc above)
            // so the connection created on acceptance has instructorUid set — this is what
            // lets the instructor see and activate the pending connection.
            assignedInstructorUid = instrUid,
            assignedInstructorName = instrName
        )

        authRepo.submitInternshipApplication(
            application = application,
            onSuccess = { _ ->
                if (_binding == null) return@submitInternshipApplication
                // Clear the whole Apply/Offer-Details back stack so Back can never resubmit — then
                // land the student on My Internship (which already shows PENDING applications).
                Toast.makeText(requireContext(), "Application sent successfully!", Toast.LENGTH_SHORT).show()
                goToMyInternship()
            },
            onFailure = { msg ->
                if (_binding == null) return@submitInternshipApplication
                if (msg == "DUPLICATE_APPLICATION") {
                    // A race won us here — someone (this device or another) already applied.
                    Toast.makeText(requireContext(),
                        "You already applied to this internship.", Toast.LENGTH_LONG).show()
                    goToMyInternship()
                    return@submitInternshipApplication
                }
                binding.btnSubmitApplication.isEnabled = true
                binding.btnSubmitApplication.text = "Submit Application"
                Toast.makeText(requireContext(), "Submission failed. Please try again.", Toast.LENGTH_LONG).show()
            }
        )
    }

    /**
     * Pops the entire Apply/Offer-Details back stack, then opens My Internship. Guarantees that
     * pressing Back from wherever we came from cannot re-submit the application, and that the user
     * lands on the screen that shows their pending application status.
     */
    private fun goToMyInternship() {
        val dash = requireActivity() as? StudentDashBoard ?: return
        val fm = requireActivity().supportFragmentManager
        // Clear any back-stack entries — Offer Details and any prior detail screens go away.
        while (fm.backStackEntryCount > 0) fm.popBackStackImmediate()
        dash.openDetail(MyInternshipFragment())
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
        // Chrome is restored by the destination fragment's onResume (returning to Offer
        // Details keeps its own header). Re-showing here would race that screen.
        _binding = null
    }
}
