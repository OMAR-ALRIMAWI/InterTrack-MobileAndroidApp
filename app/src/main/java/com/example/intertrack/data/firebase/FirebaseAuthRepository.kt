package com.example.intertrack.data.firebase

import android.util.Log
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.AdminLog
import com.example.intertrack.data.model.AdminStats
import com.example.intertrack.data.model.FirestoreCompany
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.data.model.FirestoreConversation
import com.example.intertrack.data.model.FirestoreDocumentMeta
import com.example.intertrack.data.model.FirestoreMessage
import com.example.intertrack.data.model.FirestoreNotification
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.data.model.PendingUser
import com.example.intertrack.data.model.StudentVerification
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.data.repository.AuthRepository
import com.example.intertrack.data.repository.InstructorConfirmationOutcome
import com.example.intertrack.data.repository.AddInstructorOutcome
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.data.model.isValidActive
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Login ─────────────────────────────────────────────────────────────────

    override fun login(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    onFailure("Authentication error: no user ID returned.")
                    return@addOnSuccessListener
                }
                fetchAndValidateUser(uid, onSuccess, onFailure)
            }
            .addOnFailureListener { e ->
                onFailure(mapAuthError(e))
            }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    override fun register(
        email: String,
        password: String,
        fullName: String,
        role: UserRole,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    onFailure("Registration error: no user ID returned.")
                    return@addOnSuccessListener
                }
                val user = User(
                    uid = uid,
                    email = email.trim().lowercase(),
                    fullName = fullName.trim(),
                    role = role.value,
                    accountStatus = AccountStatus.PENDING.value
                )
                createUserDocument(uid, user, onSuccess, onFailure)
            }
            .addOnFailureListener { e ->
                onFailure(mapAuthError(e))
            }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    override fun logout() {
        auth.signOut()
    }

    // ── Current user ──────────────────────────────────────────────────────────

    override fun currentFirebaseUser(): FirebaseUser? = auth.currentUser

    // ── Fetch user document ───────────────────────────────────────────────────

    override fun fetchUserDocument(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchAndValidateUser(uid, onSuccess, onFailure)
    }

    // ── Profile updates ───────────────────────────────────────────────────────

    override fun updateUserProfile(
        uid: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allUpdates = updates.toMutableMap()
        allUpdates["updatedAt"] = FieldValue.serverTimestamp()
        firestore.collection("users").document(uid)
            .set(allUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not update profile.")
            }
    }

    // ── Student verification ──────────────────────────────────────────────────

    override fun saveStudentVerification(
        verification: StudentVerification,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val doc = hashMapOf(
            "uid" to verification.uid,
            "role" to verification.role,
            "personalIdNumber" to verification.personalIdNumber,
            "universityIdNumber" to verification.universityIdNumber,
            "university" to verification.university,
            "universityKey" to com.example.intertrack.data.model.UniversityUtil.keyForDisplayName(verification.university),
            "major" to verification.major,
            "academicYear" to verification.academicYear,
            "personalIdFileName" to verification.personalIdFileName,
            "universityIdFileName" to verification.universityIdFileName,
            "personalIdSelected" to true,
            "universityIdSelected" to true,
            "status" to "PENDING",
            "submittedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("verifications").document(verification.uid)
            .set(doc)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not save verification details.")
            }
    }

    override fun fetchStudentVerification(
        uid: String,
        onSuccess: (StudentVerification?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("verifications").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }
                onSuccess(
                    StudentVerification(
                        uid = doc.getString("uid") ?: uid,
                        role = doc.getString("role") ?: "",
                        personalIdNumber = doc.getString("personalIdNumber") ?: "",
                        universityIdNumber = doc.getString("universityIdNumber") ?: "",
                        university = doc.getString("university") ?: "",
                        major = doc.getString("major") ?: "",
                        academicYear = doc.getString("academicYear") ?: "",
                        personalIdFileName = doc.getString("personalIdFileName"),
                        universityIdFileName = doc.getString("universityIdFileName"),
                        submittedAt = doc.getTimestamp("submittedAt")
                    )
                )
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load verification details.")
            }
    }

    // ── Company Supervisor verification ───────────────────────────────────────

    override fun saveCompanyVerification(
        verification: com.example.intertrack.data.model.CompanyVerification,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val doc = hashMapOf(
            "uid" to verification.uid,
            "role" to UserRole.COMPANY_SUPERVISOR.value,
            "companyName" to verification.companyName,
            "companyEmail" to verification.companyEmail,
            "companyRegistrationNumber" to verification.companyRegistrationNumber,
            "companyAddress" to verification.companyAddress,
            "companyPhone" to verification.companyPhone,
            "companyWebsite" to verification.companyWebsite,
            "supervisorFullName" to verification.supervisorFullName,
            "supervisorPosition" to verification.supervisorPosition,
            "commercialRegistrationFileName" to verification.commercialRegistrationFileName,
            "commercialRegistrationMimeType" to verification.commercialRegistrationMimeType,
            "authorizationFileName" to verification.authorizationFileName,
            "authorizationMimeType" to verification.authorizationMimeType,
            "proofUrl" to verification.proofUrl,
            "status" to "PENDING",
            "submittedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("verifications").document(verification.uid)
            .set(doc)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not save verification details.") }
    }

    override fun fetchCompanyVerification(
        uid: String,
        onSuccess: (com.example.intertrack.data.model.CompanyVerification?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("verifications").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onSuccess(null); return@addOnSuccessListener }
                onSuccess(
                    com.example.intertrack.data.model.CompanyVerification(
                        uid = doc.getString("uid") ?: uid,
                        role = doc.getString("role") ?: "",
                        companyName = doc.getString("companyName") ?: "",
                        companyEmail = doc.getString("companyEmail") ?: "",
                        companyRegistrationNumber = doc.getString("companyRegistrationNumber") ?: "",
                        companyAddress = doc.getString("companyAddress") ?: "",
                        companyPhone = doc.getString("companyPhone") ?: "",
                        companyWebsite = doc.getString("companyWebsite") ?: "",
                        supervisorFullName = doc.getString("supervisorFullName") ?: "",
                        supervisorPosition = doc.getString("supervisorPosition") ?: "",
                        commercialRegistrationFileName = doc.getString("commercialRegistrationFileName"),
                        commercialRegistrationMimeType = doc.getString("commercialRegistrationMimeType") ?: "",
                        authorizationFileName = doc.getString("authorizationFileName"),
                        authorizationMimeType = doc.getString("authorizationMimeType") ?: "",
                        proofUrl = doc.getString("proofUrl"),
                        status = doc.getString("status") ?: "PENDING",
                        submittedAt = doc.getTimestamp("submittedAt")
                    )
                )
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load verification details.") }
    }

    // ── Companies directory ───────────────────────────────────────────────────

    override fun fetchCompanies(
        onSuccess: (List<FirestoreCompany>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("companies")
            .get()
            .addOnSuccessListener { result ->
                val companies = result.documents.map { doc ->
                    FirestoreCompany(
                        companyId = doc.id,
                        supervisorUid = doc.getString("supervisorUid") ?: "",
                        name = doc.getString("name") ?: "",
                        city = doc.getString("city") ?: "",
                        industry = doc.getString("industry") ?: "",
                        description = doc.getString("description") ?: "",
                        size = doc.getString("size") ?: "",
                        website = doc.getString("website") ?: ""
                    )
                }
                onSuccess(companies)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load companies.")
            }
    }

    override fun fetchCompany(
        companyId: String,
        onSuccess: (FirestoreCompany?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("companies").document(companyId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }
                onSuccess(
                    FirestoreCompany(
                        companyId = doc.id,
                        supervisorUid = doc.getString("supervisorUid") ?: "",
                        name = doc.getString("name") ?: "",
                        city = doc.getString("city") ?: "",
                        industry = doc.getString("industry") ?: "",
                        description = doc.getString("description") ?: "",
                        size = doc.getString("size") ?: "",
                        website = doc.getString("website") ?: ""
                    )
                )
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load company details.")
            }
    }

    override fun resolveCompanyNameForSupervisor(
        supervisorUid: String,
        fallbackSnapshot: String,
        onResult: (String) -> Unit
    ) {
        val fallback = fallbackSnapshot.trim()
        if (supervisorUid.isBlank()) { onResult(fallback); return }

        // 1) users/{supervisorUid}.companyName — canonical + always updated post-verification.
        firestore.collection("users").document(supervisorUid).get()
            .addOnSuccessListener { userDoc ->
                val fromUser = (userDoc.getString("companyName") ?: "").trim()
                if (fromUser.isNotBlank()) { onResult(fromUser); return@addOnSuccessListener }

                // 2) verifications/{supervisorUid}.companyName — captured at company verification.
                firestore.collection("verifications").document(supervisorUid).get()
                    .addOnSuccessListener { vDoc ->
                        val fromVerification = (vDoc.getString("companyName") ?: "").trim()
                        if (fromVerification.isNotBlank()) { onResult(fromVerification); return@addOnSuccessListener }

                        // 3) companies where supervisorUid == this supervisor.
                        firestore.collection("companies")
                            .whereEqualTo("supervisorUid", supervisorUid)
                            .limit(1).get()
                            .addOnSuccessListener { qs ->
                                val fromCompany = qs.documents.firstOrNull()
                                    ?.getString("name")?.trim().orEmpty()
                                onResult(if (fromCompany.isNotBlank()) fromCompany else fallback)
                            }
                            .addOnFailureListener { onResult(fallback) }
                    }
                    .addOnFailureListener { onResult(fallback) }
            }
            .addOnFailureListener { onResult(fallback) }
    }

    override fun saveCompany(
        companyId: String?,
        supervisorUid: String,
        data: Map<String, Any?>,
        onSuccess: (newCompanyId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allData = data.toMutableMap()
        allData["supervisorUid"] = supervisorUid
        allData["updatedAt"] = FieldValue.serverTimestamp()

        if (!companyId.isNullOrBlank()) {
            firestore.collection("companies").document(companyId)
                .set(allData, SetOptions.merge())
                .addOnSuccessListener { onSuccess(companyId) }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not save company.") }
        } else {
            val newRef = firestore.collection("companies").document()
            allData["createdAt"] = FieldValue.serverTimestamp()
            newRef.set(allData)
                .addOnSuccessListener {
                    val newId = newRef.id
                    firestore.collection("users").document(supervisorUid)
                        .set(mapOf("companyId" to newId, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                        .addOnSuccessListener { onSuccess(newId) }
                        .addOnFailureListener { e -> onFailure(e.message ?: "Company created but link failed.") }
                }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not create company.") }
        }
    }

    // ── Instructor directory ──────────────────────────────────────────────────

    override fun fetchActiveInstructors(
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val callerUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        Log.d("FindInstructor", "fetchActiveInstructors — callerUid=$callerUid filter=role:INSTRUCTOR+accountStatus:ACTIVE")
        firestore.collection("users")
            .whereEqualTo("role", UserRole.INSTRUCTOR.value)
            .whereEqualTo("accountStatus", AccountStatus.ACTIVE.value)
            .get()
            .addOnSuccessListener { result ->
                val instructors = result.documents.mapNotNull { doc ->
                    val role = doc.getString("role") ?: return@mapNotNull null
                    val status = doc.getString("accountStatus") ?: return@mapNotNull null
                    User(
                        uid = doc.getString("uid") ?: doc.id,
                        email = doc.getString("email") ?: "",
                        fullName = doc.getString("fullName") ?: "",
                        role = role,
                        accountStatus = status,
                        university = doc.getString("university"),
                        universityKey = doc.getString("universityKey"),
                        department = doc.getString("department"),
                        bio = doc.getString("bio"),
                        office = doc.getString("office")
                    )
                }
                Log.d("FindInstructor", "fetchActiveInstructors returned ${instructors.size} instructors")
                onSuccess(instructors)
            }
            .addOnFailureListener { e ->
                Log.e("FindInstructor", "fetchActiveInstructors failed: ${e.message}")
                onFailure(e.message ?: "Could not load instructors.")
            }
    }

    // ── Internship offers ─────────────────────────────────────────────────────

    override fun publishInternshipOffer(
        offer: FirestoreInternshipOffer,
        onSuccess: (offerId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("internshipOffers").document()
        val doc = hashMapOf(
            "offerId" to ref.id,
            "supervisorUid" to offer.supervisorUid,
            "companyId" to offer.companyId,
            "companyName" to offer.companyName,
            "supervisorName" to offer.supervisorName,
            "title" to offer.title,
            "department" to offer.department,
            "description" to offer.description,
            "requirements" to offer.requirements,
            "duration" to offer.duration,
            "seats" to offer.seats,
            "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(doc)
            .addOnSuccessListener { onSuccess(ref.id) }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not publish offer.") }
    }

    override fun getCompanyActiveOffers(
        supervisorUid: String,
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers")
            .whereEqualTo("supervisorUid", supervisorUid)
            .get()
            .addOnSuccessListener { result ->
                val sorted = result.documents.mapNotNull { docToOffer(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(sorted)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load offers.") }
    }

    override fun getOpenInternshipOffers(
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers")
            .whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener { result ->
                val sorted = result.documents.mapNotNull { docToOffer(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(sorted)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load open offers.") }
    }

    override fun getOfferById(
        offerId: String,
        onSuccess: (FirestoreInternshipOffer?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (offerId.isBlank()) { onSuccess(null); return }
        firestore.collection("internshipOffers").document(offerId)
            .get()
            .addOnSuccessListener { doc ->
                onSuccess(if (doc.exists()) docToOffer(doc) else null)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load the offer.") }
    }

    override fun closeInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers").document(offerId)
            .set(
                mapOf("status" to "CLOSED", "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not close offer.") }
    }

    override fun reopenInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("internshipOffers").document(offerId)
        // Guarded read first: only a CLOSED offer may be reopened — never DELETED (no restore).
        ref.get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("status") ?: ""
                if (!doc.exists() || status != "CLOSED") {
                    onFailure(
                        if (status == "DELETED") "Deleted offers cannot be reopened."
                        else "Only closed offers can be reopened."
                    )
                    return@addOnSuccessListener
                }
                ref.set(
                    mapOf(
                        "status" to "OPEN",
                        "reopenedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not reopen offer.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not reopen offer.") }
    }

    override fun updateInternshipOffer(
        offerId: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allUpdates = updates.toMutableMap()
        allUpdates["updatedAt"] = FieldValue.serverTimestamp()
        firestore.collection("internshipOffers").document(offerId)
            .set(allUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not update offer.") }
    }

    override fun deleteInternshipOffer(
        offerId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers").document(offerId)
            .set(
                mapOf("status" to "DELETED", "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not delete offer.") }
    }

    // ── Internship applications ───────────────────────────────────────────────

    override fun submitInternshipApplication(
        application: FirestoreInternshipApplication,
        onSuccess: (applicationId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // DB-level dedup guard: even with the fragment's UI checks, a rapid double-tap or a
        // second-device race could try to create two docs for the same student+offer. Query first,
        // fail-fast if an active application/connection already exists, and only then write. The
        // caller signals a duplicate to the UI via the sentinel error string "DUPLICATE_APPLICATION".
        val appsQuery = firestore.collection("internshipApplications")
            .whereEqualTo("studentUid", application.studentUid)
        appsQuery.get()
            .addOnSuccessListener { appsResult ->
                val hasBlockingApp = appsResult.documents.any { d ->
                    val status = d.getString("status") ?: ""
                    val sameOffer =
                        (application.offerId.isNotBlank() && d.getString("offerId") == application.offerId) ||
                        (application.offerId.isBlank() && d.getString("companyId") == application.companyId)
                    sameOffer && (status == "PENDING" || status == "ACCEPTED")
                }
                if (hasBlockingApp) { onFailure("DUPLICATE_APPLICATION"); return@addOnSuccessListener }

                // Also block if a connection already exists for the same student + offer/company
                // (in case the application was already accepted → connection created).
                val connsQuery = firestore.collection("internshipConnections")
                    .whereEqualTo("studentUid", application.studentUid)
                connsQuery.get()
                    .addOnSuccessListener { connsResult ->
                        val hasBlockingConn = connsResult.documents.any { c ->
                            val status = c.getString("status") ?: ""
                            val sameOffer =
                                (application.offerId.isNotBlank() && c.getString("internshipId") == application.offerId) ||
                                (application.offerId.isBlank() && c.getString("companyId") == application.companyId)
                            sameOffer && (status == "WAITING_INSTRUCTOR_CONNECTION" ||
                                status == "ACTIVE" || status == "COMPLETED")
                        }
                        if (hasBlockingConn) { onFailure("DUPLICATE_APPLICATION"); return@addOnSuccessListener }
                        writeApplicationDoc(application, onSuccess, onFailure)
                    }
                    // Connection read failing shouldn't block a legit submit; write anyway.
                    .addOnFailureListener { writeApplicationDoc(application, onSuccess, onFailure) }
            }
            // Application read failing shouldn't block a legit submit; write anyway.
            .addOnFailureListener { writeApplicationDoc(application, onSuccess, onFailure) }
    }

    private fun writeApplicationDoc(
        application: FirestoreInternshipApplication,
        onSuccess: (applicationId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("internshipApplications").document()
        val doc = hashMapOf(
            "applicationId" to ref.id,
            "studentUid" to application.studentUid,
            "studentName" to application.studentName,
            "studentEmail" to application.studentEmail,
            "studentUniversity" to application.studentUniversity,
            "studentMajor" to application.studentMajor,
            "studentYearLevel" to application.studentYearLevel,
            "studentPhone" to application.studentPhone,
            "studentGpa" to application.studentGpa,
            "studentSkills" to application.studentSkills,
            "motivation" to application.motivation,
            "startDate" to application.startDate,
            "duration" to application.duration,
            "preferredDepartment" to application.preferredDepartment,
            "previousExperience" to application.previousExperience,
            "portfolioLink" to application.portfolioLink,
            "companyId" to application.companyId,
            "companyName" to application.companyName,
            "supervisorUid" to application.supervisorUid,
            "offerId" to application.offerId,
            "offerTitle" to application.offerTitle,
            "assignedInstructorUid" to application.assignedInstructorUid,
            "assignedInstructorName" to application.assignedInstructorName,
            "status" to "PENDING",
            "rejectionReason" to "",
            "reviewedByUid" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(doc)
            .addOnSuccessListener { onSuccess(ref.id) }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not submit application.") }
    }

    override fun getApplicationByStudentAndCompany(
        studentUid: String,
        companyId: String,
        onSuccess: (FirestoreInternshipApplication?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipApplications")
            .whereEqualTo("studentUid", studentUid)
            .whereEqualTo("companyId", companyId)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val doc = result.documents.firstOrNull()
                if (doc == null) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }
                onSuccess(docToApplication(doc))
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not check existing application.") }
    }

    override fun getStudentApplications(
        studentUid: String,
        onSuccess: (List<FirestoreInternshipApplication>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipApplications")
            .whereEqualTo("studentUid", studentUid)
            .get()
            .addOnSuccessListener { result ->
                val sorted = result.documents.mapNotNull { docToApplication(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(sorted)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load applications.") }
    }

    override fun getCompanyApplications(
        supervisorUid: String,
        onSuccess: (List<FirestoreInternshipApplication>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users").document(supervisorUid).get()
            .addOnSuccessListener { userDoc ->
                val companyId = userDoc.getString("companyId")
                val query = if (!companyId.isNullOrBlank()) {
                    firestore.collection("internshipApplications")
                        .whereEqualTo("companyId", companyId)
                } else {
                    firestore.collection("internshipApplications")
                        .whereEqualTo("supervisorUid", supervisorUid)
                }
                query.get()
                    .addOnSuccessListener { result ->
                        val sorted = result.documents.mapNotNull { docToApplication(it) }
                            .sortedByDescending { it.createdAt?.seconds ?: 0L }
                        onSuccess(sorted)
                    }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not load applications.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load applications.") }
    }

    override fun acceptInternshipApplication(
        applicationId: String,
        reviewerUid: String,
        startDateMs: Long,
        endDateMs: Long,
        requiredReportsCount: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val appRef = firestore.collection("internshipApplications").document(applicationId)
        appRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onFailure("Application not found."); return@addOnSuccessListener }
                val currentStatus = doc.getString("status") ?: ""
                if (currentStatus != "PENDING") {
                    onFailure("Application is already $currentStatus."); return@addOnSuccessListener
                }
                val studentUid = doc.getString("studentUid") ?: ""
                val companyId = doc.getString("companyId") ?: ""
                val companyName = doc.getString("companyName") ?: ""
                val offerId = doc.getString("offerId") ?: ""
                val offerTitle = doc.getString("offerTitle") ?: ""

                val studentName = doc.getString("studentName") ?: ""
                val supervisorName = doc.getString("supervisorName") ?: ""
                val assignedInstructorUid = doc.getString("assignedInstructorUid") ?: ""
                val assignedInstructorName = doc.getString("assignedInstructorName") ?: ""

                // The company supervisor sets the internship period at accept time.
                val startTs = com.google.firebase.Timestamp(java.util.Date(startDateMs))
                val endTs = com.google.firebase.Timestamp(java.util.Date(endDateMs))

                appRef.set(
                    mapOf(
                        "status" to "ACCEPTED",
                        "reviewedByUid" to reviewerUid,
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        // Persist the period on the application too, so a later-created connection can copy it.
                        "startDate" to startTs,
                        "endDate" to endTs,
                        "reportFrequency" to "WEEKLY",
                        "requiredReportsCount" to requiredReportsCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    // Create connection with WAITING_INSTRUCTOR_CONNECTION status + the company's period.
                    val connRef = firestore.collection("internshipConnections").document(applicationId)
                    connRef.get().addOnSuccessListener { connDoc ->
                        if (!connDoc.exists()) {
                            connRef.set(hashMapOf(
                                "connectionId" to applicationId,
                                "applicationId" to applicationId,
                                "studentUid" to studentUid,
                                "studentName" to studentName,
                                "companyId" to companyId,
                                "companyName" to companyName,
                                "supervisorUid" to reviewerUid,
                                "supervisorName" to supervisorName,
                                "internshipId" to offerId,
                                "internshipTitle" to offerTitle,
                                "instructorUid" to assignedInstructorUid,
                                "instructorName" to assignedInstructorName,
                                "status" to "WAITING_INSTRUCTOR_CONNECTION",
                                "connectedAt" to null,
                                "connectedByInstructorUid" to "",
                                "startDate" to startTs,
                                "endDate" to endTs,
                                "reportFrequency" to "WEEKLY",
                                "requiredReportsCount" to requiredReportsCount,
                                "submittedReportsCount" to 0,
                                "periodSetByUid" to reviewerUid,
                                "periodSetByRole" to "COMPANY_SUPERVISOR",
                                "periodSetAt" to FieldValue.serverTimestamp(),
                                "startedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ))
                        } else {
                            // Connection already exists (rare) — merge the period onto it.
                            connRef.set(mapOf(
                                "startDate" to startTs,
                                "endDate" to endTs,
                                "reportFrequency" to "WEEKLY",
                                "requiredReportsCount" to requiredReportsCount,
                                "periodSetByUid" to reviewerUid,
                                "periodSetByRole" to "COMPANY_SUPERVISOR",
                                "periodSetAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ), SetOptions.merge())
                        }
                    }
                    // Notify student — mention waiting for instructor
                    if (studentUid.isNotBlank()) {
                        val notifRef = firestore.collection("notifications").document()
                        val msg = buildString {
                            append("Your application")
                            if (offerTitle.isNotBlank()) append(" for $offerTitle")
                            if (companyName.isNotBlank()) append(" at $companyName")
                            append(" was accepted. Waiting for your instructor to connect you with the company.")
                        }
                        notifRef.set(hashMapOf(
                            "notificationId" to notifRef.id,
                            "recipientUid" to studentUid,
                            "senderUid" to reviewerUid,
                            "type" to "INTERNSHIP_APPLICATION_ACCEPTED",
                            "title" to "Application Accepted",
                            "message" to msg,
                            "relatedId" to applicationId,
                            "relatedCompanyId" to companyId,
                            "relatedInternshipId" to offerId,
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    // Notify instructor if already assigned
                    if (assignedInstructorUid.isNotBlank()) {
                        val instrNotif = firestore.collection("notifications").document()
                        instrNotif.set(hashMapOf(
                            "notificationId" to instrNotif.id,
                            "recipientUid" to assignedInstructorUid,
                            "senderUid" to reviewerUid,
                            "type" to "INTERNSHIP_NEEDS_INSTRUCTOR_CONNECTION",
                            "title" to "Internship Connection Needed",
                            "message" to "$studentName was accepted by $companyName. Please connect them from the Requests tab.",
                            "relatedId" to applicationId,
                            "relatedCompanyId" to companyId,
                            "relatedInternshipId" to offerId,
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    // Period-set notifications (student, supervisor, instructor-if-assigned).
                    sendPeriodSetNotifications(
                        connectionId = applicationId,
                        studentUid = studentUid,
                        studentName = studentName,
                        supervisorUid = reviewerUid,
                        instructorUid = assignedInstructorUid,
                        companyId = companyId,
                        internshipId = offerId,
                        startDateMs = startDateMs,
                        endDateMs = endDateMs
                    )
                    onSuccess()
                }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not accept application.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load application.") }
    }

    /**
     * Sends INTERNSHIP_PERIOD_SET notifications to the related users only (student, company
     * supervisor, and the instructor if already assigned). Never notifies admin/unrelated users.
     * The period dates are formatted yyyy/MM/dd to match the app's date display.
     */
    private fun sendPeriodSetNotifications(
        connectionId: String,
        studentUid: String,
        studentName: String,
        supervisorUid: String,
        instructorUid: String,
        companyId: String,
        internshipId: String,
        startDateMs: Long,
        endDateMs: Long
    ) {
        val fmt = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val startStr = fmt.format(java.util.Date(startDateMs))
        val endStr = fmt.format(java.util.Date(endDateMs))
        val name = studentName.ifBlank { "the student" }

        fun send(recipientUid: String, body: String) {
            if (recipientUid.isBlank()) return
            val ref = firestore.collection("notifications").document()
            ref.set(hashMapOf(
                "notificationId" to ref.id,
                "recipientUid" to recipientUid,
                "senderUid" to supervisorUid,
                "type" to "INTERNSHIP_PERIOD_SET",
                "title" to "Internship Period Set",
                "message" to body,
                "relatedId" to connectionId,
                "relatedCompanyId" to companyId,
                "relatedInternshipId" to internshipId,
                "studentUid" to studentUid,
                "instructorUid" to instructorUid,
                "companySupervisorUid" to supervisorUid,
                "connectionId" to connectionId,
                "startDate" to com.google.firebase.Timestamp(java.util.Date(startDateMs)),
                "endDate" to com.google.firebase.Timestamp(java.util.Date(endDateMs)),
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }

        send(studentUid, "Your internship period has been set from $startStr to $endStr.")
        send(supervisorUid, "You set the internship period for $name from $startStr to $endStr.")
        send(instructorUid, "The internship period for $name has been set from $startStr to $endStr.")
    }

    override fun rejectInternshipApplication(
        applicationId: String,
        reviewerUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val appRef = firestore.collection("internshipApplications").document(applicationId)
        appRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onFailure("Application not found."); return@addOnSuccessListener }
                val currentStatus = doc.getString("status") ?: ""
                if (currentStatus != "PENDING") {
                    onFailure("Application is already $currentStatus."); return@addOnSuccessListener
                }
                val studentUid = doc.getString("studentUid") ?: ""
                val companyId = doc.getString("companyId") ?: ""
                val companyName = doc.getString("companyName") ?: ""
                val offerId = doc.getString("offerId") ?: ""
                val offerTitle = doc.getString("offerTitle") ?: ""

                appRef.set(
                    mapOf(
                        "status" to "REJECTED",
                        "rejectionReason" to reason,
                        "reviewedByUid" to reviewerUid,
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    if (studentUid.isNotBlank()) {
                        val notifRef = firestore.collection("notifications").document()
                        val msg = buildString {
                            append("Your application")
                            if (offerTitle.isNotBlank()) append(" for $offerTitle")
                            if (companyName.isNotBlank()) append(" at $companyName")
                            append(" was rejected.")
                            if (reason.isNotBlank()) append(" Reason: $reason")
                        }
                        notifRef.set(hashMapOf(
                            "notificationId" to notifRef.id,
                            "recipientUid" to studentUid,
                            "senderUid" to reviewerUid,
                            "type" to "INTERNSHIP_APPLICATION_REJECTED",
                            "title" to "Application Rejected",
                            "message" to msg,
                            "relatedId" to applicationId,
                            "relatedCompanyId" to companyId,
                            "relatedInternshipId" to offerId,
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    onSuccess()
                }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not reject application.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load application.") }
    }

    // ── Instructor requests ───────────────────────────────────────────────────

    override fun submitInstructorRequest(
        request: FirestoreInstructorRequest,
        onSuccess: (requestId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("instructorRequests").document()
        val doc = hashMapOf(
            "requestId" to ref.id,
            "studentUid" to request.studentUid,
            "studentName" to request.studentName,
            "studentEmail" to request.studentEmail,
            "studentUniversity" to request.studentUniversity,
            "studentMajor" to request.studentMajor,
            "studentGpa" to request.studentGpa,
            "studentCompanyName" to request.studentCompanyName,
            "instructorUid" to request.instructorUid,
            "instructorName" to request.instructorName,
            "instructorEmail" to request.instructorEmail,
            "status" to "PENDING",
            "rejectionReason" to "",
            "reviewedByUid" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(doc)
            .addOnSuccessListener { onSuccess(ref.id) }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not send request.") }
    }

    override fun getRequestByStudentAndInstructor(
        studentUid: String,
        instructorUid: String,
        onSuccess: (FirestoreInstructorRequest?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("instructorRequests")
            .whereEqualTo("studentUid", studentUid)
            .whereEqualTo("instructorUid", instructorUid)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val doc = result.documents.firstOrNull()
                if (doc == null) {
                    onSuccess(null)
                    return@addOnSuccessListener
                }
                onSuccess(docToRequest(doc))
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not check existing request.") }
    }

    override fun getInstructorRequests(
        instructorUid: String,
        onSuccess: (List<FirestoreInstructorRequest>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("instructorRequests")
            .whereEqualTo("instructorUid", instructorUid)
            .get()
            .addOnSuccessListener { result ->
                val sorted = result.documents.mapNotNull { docToRequest(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(sorted)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load requests.") }
    }

    override fun acceptInstructorRequest(
        requestId: String,
        studentUid: String,
        instructorUid: String,
        instructorName: String,
        reviewerUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val requestRef = firestore.collection("instructorRequests").document(requestId)
        val studentRef = firestore.collection("users").document(studentUid)

        firestore.runBatch { batch ->
            batch.set(
                requestRef,
                mapOf(
                    "status" to "ACCEPTED",
                    "reviewedByUid" to reviewerUid,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            batch.set(
                studentRef,
                mapOf(
                    "assignedInstructorUid" to instructorUid,
                    "assignedInstructorName" to instructorName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }
            .addOnSuccessListener {
                if (studentUid.isNotBlank()) {
                    val notifRef = firestore.collection("notifications").document()
                    val displayName = instructorName.ifBlank { "your instructor" }
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to studentUid,
                        "senderUid" to instructorUid,
                        "type" to "INSTRUCTOR_REQUEST_ACCEPTED",
                        "title" to "Instructor Request Accepted",
                        "message" to "Your instructor request was accepted by $displayName.",
                        "relatedId" to requestId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not accept request.") }
    }

    override fun rejectInstructorRequest(
        requestId: String,
        reviewerUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val reqRef = firestore.collection("instructorRequests").document(requestId)
        reqRef.get()
            .addOnSuccessListener { doc ->
                val studentUid = doc.getString("studentUid") ?: ""
                val instructorName = doc.getString("instructorName") ?: ""
                reqRef.set(
                    mapOf(
                        "status" to "REJECTED",
                        "rejectionReason" to reason,
                        "reviewedByUid" to reviewerUid,
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    if (studentUid.isNotBlank()) {
                        val notifRef = firestore.collection("notifications").document()
                        val displayName = instructorName.ifBlank { "the instructor" }
                        notifRef.set(hashMapOf(
                            "notificationId" to notifRef.id,
                            "recipientUid" to studentUid,
                            "senderUid" to reviewerUid,
                            "type" to "INSTRUCTOR_REQUEST_REJECTED",
                            "title" to "Instructor Request Declined",
                            "message" to "Your instructor request was declined by $displayName.",
                            "relatedId" to requestId,
                            "relatedCompanyId" to "",
                            "relatedInternshipId" to "",
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    onSuccess()
                }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not reject request.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not reject request.") }
    }

    // ── Admin: pending users ──────────────────────────────────────────────────

    override fun fetchPendingUsers(
        onSuccess: (List<PendingUser>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users")
            .whereEqualTo("accountStatus", AccountStatus.PENDING.value)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    val role = doc.getString("role") ?: return@mapNotNull null
                    if (role == UserRole.ADMIN.value) return@mapNotNull null
                    PendingUser(
                        uid = doc.getString("uid") ?: doc.id,
                        fullName = doc.getString("fullName") ?: "",
                        email = doc.getString("email") ?: "",
                        role = role,
                        registeredAt = doc.getTimestamp("createdAt")
                    )
                }
                onSuccess(list)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load pending users.")
            }
    }

    // ── Admin: approve ────────────────────────────────────────────────────────

    override fun approveUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userRef = firestore.collection("users").document(targetUid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) throw Exception("User account not found.")
            val currentStatus = snapshot.getString("accountStatus")
            if (currentStatus != AccountStatus.PENDING.value) {
                throw Exception("Account is no longer pending (current: $currentStatus).")
            }
            transaction.update(
                userRef,
                mapOf(
                    "accountStatus" to AccountStatus.ACTIVE.value,
                    "verifiedBy" to adminUid,
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "rejectionReason" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener {
                notifyUser(targetUid, "ACCOUNT_APPROVED", "Account Approved",
                    "Your account has been approved. You now have full access.")
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Approval failed.") }
    }

    // ── Admin: reject ─────────────────────────────────────────────────────────

    override fun rejectUser(
        targetUid: String,
        adminUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userRef = firestore.collection("users").document(targetUid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) throw Exception("User account not found.")
            val currentStatus = snapshot.getString("accountStatus")
            if (currentStatus != AccountStatus.PENDING.value) {
                throw Exception("Account is no longer pending (current: $currentStatus).")
            }
            transaction.update(
                userRef,
                mapOf(
                    "accountStatus" to AccountStatus.REJECTED.value,
                    "verifiedBy" to adminUid,
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "rejectionReason" to reason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener {
                // Include the exact typed reason in the user's notification when present.
                val reasonSuffix = if (reason.isNotBlank()) " Reason: $reason" else ""
                notifyUser(targetUid, "ACCOUNT_REJECTED", "Verification Rejected",
                    "Your account verification was rejected.$reasonSuffix")
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Rejection failed.") }
    }

    // ── Admin: all users ──────────────────────────────────────────────────────

    override fun fetchAllUsers(
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val users = result.documents.mapNotNull { doc ->
                    val role = doc.getString("role") ?: return@mapNotNull null
                    val status = doc.getString("accountStatus") ?: return@mapNotNull null
                    if (role == UserRole.ADMIN.value) return@mapNotNull null
                    User(
                        uid = doc.getString("uid") ?: doc.id,
                        email = doc.getString("email") ?: "",
                        fullName = doc.getString("fullName") ?: "",
                        role = role,
                        accountStatus = status,
                        rejectionReason = doc.getString("rejectionReason")
                    )
                }
                onSuccess(users)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load accounts.")
            }
    }

    // ── Admin: block / unblock / delete ───────────────────────────────────────

    override fun blockUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userRef = firestore.collection("users").document(targetUid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) throw Exception("User not found.")
            if (snapshot.getString("role") == UserRole.ADMIN.value) {
                throw Exception("Cannot block an administrator account.")
            }
            transaction.update(
                userRef,
                mapOf(
                    "accountStatus" to AccountStatus.BLOCKED.value,
                    "blockedAt" to FieldValue.serverTimestamp(),
                    "blockedByAdminUid" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Block failed.") }
    }

    override fun unblockUser(
        targetUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users").document(targetUid)
            .update(
                mapOf(
                    "accountStatus" to AccountStatus.ACTIVE.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Unblock failed.") }
    }

    override fun deleteUser(
        targetUid: String,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userRef = firestore.collection("users").document(targetUid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) throw Exception("User not found.")
            if (snapshot.getString("role") == UserRole.ADMIN.value) {
                throw Exception("Cannot delete an administrator account.")
            }
            if (targetUid == adminUid) {
                throw Exception("Cannot delete your own account.")
            }
            transaction.update(
                userRef,
                mapOf(
                    "accountStatus" to AccountStatus.DELETED.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Delete failed.") }
    }

    // ── Admin: dashboard stats ────────────────────────────────────────────────

    override fun fetchAdminStats(
        onSuccess: (AdminStats) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users").get()
            .addOnSuccessListener { result ->
                val all = result.documents.filter {
                    it.getString("role") != UserRole.ADMIN.value
                }
                onSuccess(
                    AdminStats(
                        totalAccounts = all.size,
                        totalStudents = all.count { it.getString("role") == UserRole.STUDENT.value },
                        totalInstructors = all.count { it.getString("role") == UserRole.INSTRUCTOR.value },
                        totalCompanySupervisors = all.count { it.getString("role") == UserRole.COMPANY_SUPERVISOR.value },
                        pendingRequests = all.count { it.getString("accountStatus") == AccountStatus.PENDING.value },
                        blockedAccounts = all.count { it.getString("accountStatus") == AccountStatus.BLOCKED.value }
                    )
                )
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load statistics.")
            }
    }

    override fun fetchRecentActivity(
        onSuccess: (List<AdminLog>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(15)
            .get()
            .addOnSuccessListener { result ->
                val logs = result.documents.mapNotNull { doc ->
                    val status = doc.getString("accountStatus") ?: return@mapNotNull null
                    val role = doc.getString("role") ?: return@mapNotNull null
                    if (role == UserRole.ADMIN.value) return@mapNotNull null
                    AdminLog(
                        uid = doc.id,
                        fullName = doc.getString("fullName") ?: "",
                        email = doc.getString("email") ?: "",
                        role = role,
                        accountStatus = status,
                        updatedAt = doc.getTimestamp("updatedAt")
                    )
                }
                onSuccess(logs)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Could not load recent activity.")
            }
    }

    // ── Reports ────────────────────────────────────────────────────────────────

    override fun submitReport(
        report: FirestoreReport,
        onSuccess: (reportId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("reports").document()
        val doc = hashMapOf(
            "reportId" to ref.id,
            "internshipConnectionId" to report.internshipConnectionId,
            "studentUid" to report.studentUid,
            "studentName" to report.studentName,
            "companyId" to report.companyId,
            "companyName" to report.companyName,
            "internshipId" to report.internshipId,
            "internshipTitle" to report.internshipTitle,
            "instructorUid" to report.instructorUid,
            "supervisorUid" to report.supervisorUid,
            "reportTitle" to report.reportTitle,
            "reportPeriod" to report.reportPeriod,
            "reportContent" to report.reportContent,
            "challenges" to report.challenges,
            "learnedSkills" to report.learnedSkills,
            "hoursWorked" to report.hoursWorked,
            // Weekly status is SUBMITTED (on time) or LATE — decided by the caller from the deadline.
            "status" to report.status.ifBlank { "SUBMITTED" },
            "attachedFileName" to report.attachedFileName,
            "attachedFileMimeType" to report.attachedFileMimeType,
            "reportWeekNumber" to report.reportWeekNumber,
            "periodStart" to report.periodStart,
            "periodEnd" to report.periodEnd,
            "deadlineDate" to report.deadlineDate,
            "submittedAt" to FieldValue.serverTimestamp(),
            "selectedOnly" to true,
            "storageUrl" to null,
            "downloadUrl" to null,
            "instructorFeedback" to "",
            "supervisorFeedback" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(doc)
            .addOnSuccessListener {
                // Bump the connection's submitted counter (best-effort; company/instructor rules
                // allow it, student may be blocked — the UI counts reports live regardless).
                if (report.internshipConnectionId.isNotBlank()) {
                    firestore.collection("internshipConnections").document(report.internshipConnectionId)
                        .set(mapOf("submittedReportsCount" to FieldValue.increment(1)), SetOptions.merge())
                        .addOnFailureListener { /* ignore — count is derived live in the UI */ }
                }
                // Notify BOTH the assigned instructor and the company supervisor (never the student).
                val student = report.studentName.ifBlank { "A student" }
                val weekPart = if (report.reportWeekNumber > 0) "Week ${report.reportWeekNumber} " else ""
                val body = "$student submitted a new ${weekPart}internship report."
                fun notify(recipientUid: String) {
                    if (recipientUid.isBlank() || recipientUid == report.studentUid) return
                    val notifRef = firestore.collection("notifications").document()
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to recipientUid,
                        "senderUid" to report.studentUid,
                        "type" to "REPORT_SUBMITTED",
                        "title" to "New Report Submitted",
                        "message" to body,
                        "relatedId" to ref.id,               // reportId — used to open Report Details
                        "relatedCompanyId" to report.companyId,
                        "relatedInternshipId" to report.internshipId,
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                notify(report.instructorUid)
                notify(report.supervisorUid)
                if (report.instructorUid.isBlank() && report.supervisorUid.isBlank()) {
                    Log.w("ReportNotify", "report ${ref.id} has no instructor/supervisor to notify")
                }
                onSuccess(ref.id)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not submit report.") }
    }

    override fun updateReport(
        reportId: String,
        updates: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allUpdates = updates.toMutableMap()
        allUpdates["updatedAt"] = FieldValue.serverTimestamp()
        firestore.collection("reports").document(reportId)
            .set(allUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not update report.") }
    }

    override fun getReportById(
        reportId: String,
        onSuccess: (FirestoreReport?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("reports").document(reportId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onSuccess(null); return@addOnSuccessListener }
                onSuccess(docToReport(doc))
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load report.") }
    }

    override fun getStudentReports(
        studentUid: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("reports")
            .whereEqualTo("studentUid", studentUid)
            .get()
            .addOnSuccessListener { result ->
                val reports = result.documents.mapNotNull { docToReport(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(reports)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load reports.") }
    }

    override fun backfillStudentReports(
        studentUid: String,
        onDone: () -> Unit
    ) {
        getStudentActiveConnection(
            studentUid = studentUid,
            onSuccess = { conn ->
                if (conn == null) { onDone(); return@getStudentActiveConnection }
                firestore.collection("reports")
                    .whereEqualTo("studentUid", studentUid)
                    .get()
                    .addOnSuccessListener { result ->
                        val reports = result.documents.mapNotNull { docToReport(it) }
                        // A report is unrouted if it has no connection link, and it plausibly
                        // belongs to this connection (matching/blank internshipId and companyId).
                        val toFix = reports.filter { r ->
                            r.internshipConnectionId.isBlank() &&
                                (r.internshipId.isBlank() || r.internshipId == conn.internshipId) &&
                                (r.companyId.isBlank() || r.companyId == conn.companyId)
                        }
                        if (toFix.isEmpty()) { onDone(); return@addOnSuccessListener }
                        var remaining = toFix.size
                        toFix.forEach { r ->
                            val updates = hashMapOf<String, Any?>(
                                "internshipConnectionId" to conn.connectionId,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (r.instructorUid.isBlank()) updates["instructorUid"] = conn.instructorUid
                            if (r.supervisorUid.isBlank()) updates["supervisorUid"] = conn.supervisorUid
                            if (r.companyId.isBlank()) updates["companyId"] = conn.companyId
                            if (r.companyName.isBlank()) updates["companyName"] = conn.companyName
                            if (r.internshipId.isBlank()) updates["internshipId"] = conn.internshipId
                            if (r.internshipTitle.isBlank()) updates["internshipTitle"] = conn.internshipTitle
                            firestore.collection("reports").document(r.reportId)
                                .set(updates, SetOptions.merge())
                                .addOnSuccessListener {
                                    Log.d("ReportBackfill", "linked report ${r.reportId} -> conn ${conn.connectionId}")
                                    remaining--; if (remaining == 0) onDone()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ReportBackfill", "failed report ${r.reportId}", e)
                                    remaining--; if (remaining == 0) onDone()
                                }
                        }
                    }
                    .addOnFailureListener { onDone() }
            },
            onFailure = { onDone() }
        )
    }

    override fun getInstructorReports(
        instructorUid: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("reports")
            .whereEqualTo("instructorUid", instructorUid)
            .get()
            .addOnSuccessListener { result ->
                val reports = result.documents.mapNotNull { docToReport(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(reports)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load reports.") }
    }

    override fun getCompanyReports(
        companyId: String,
        onSuccess: (List<FirestoreReport>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("reports")
            .whereEqualTo("companyId", companyId)
            .get()
            .addOnSuccessListener { result ->
                val reports = result.documents.mapNotNull { docToReport(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(reports)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load reports.") }
    }

    override fun reviewReport(
        reportId: String,
        studentUid: String,
        status: String,
        feedback: String,
        reviewerUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("reports").document(reportId)
        ref.set(
            mapOf(
                "status" to status,
                "instructorFeedback" to feedback,
                "reviewedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
            .addOnSuccessListener {
                if (studentUid.isNotBlank()) {
                    val notifRef = firestore.collection("notifications").document()
                    val title = if (status == "REVISION_REQUESTED") "Report Needs Revision" else "Report Reviewed"
                    val message = if (status == "REVISION_REQUESTED")
                        "Your instructor requested revisions on your report."
                    else
                        "Your instructor reviewed your report."
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to studentUid,
                        "senderUid" to reviewerUid,
                        "type" to "REPORT_$status",
                        "title" to title,
                        "message" to message,
                        "relatedId" to reportId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not review report.") }
    }

    override fun addSupervisorFeedback(
        reportId: String,
        feedback: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("reports").document(reportId)
            .set(
                mapOf(
                    "supervisorFeedback" to feedback,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not save feedback.") }
    }

    // ── Messaging ──────────────────────────────────────────────────────────────

    override fun getOrCreateConversation(
        uidA: String,
        nameA: String,
        uidB: String,
        nameB: String,
        onSuccess: (conversationId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val convId = listOf(uidA, uidB).sorted().joinToString("_")
        val ref = firestore.collection("conversations").document(convId)
        ref.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onSuccess(convId)
                    return@addOnSuccessListener
                }
                val data = hashMapOf(
                    "conversationId" to convId,
                    "participantUids" to listOf(uidA, uidB),
                    "participantNames" to mapOf(uidA to nameA, uidB to nameB),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadBy" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(data)
                    .addOnSuccessListener { onSuccess(convId) }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not start conversation.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not start conversation.") }
    }

    override fun sendMessage(
        conversationId: String,
        senderUid: String,
        senderName: String,
        receiverUid: String,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val msgRef = firestore.collection("messages").document()
        val convRef = firestore.collection("conversations").document(conversationId)
        val msgData = hashMapOf(
            "messageId" to msgRef.id,
            "conversationId" to conversationId,
            "senderUid" to senderUid,
            "senderName" to senderName,
            "receiverUid" to receiverUid,
            "messageText" to text,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.runBatch { batch ->
            batch.set(msgRef, msgData)
            batch.set(
                convRef,
                mapOf(
                    "lastMessage" to text,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "unreadBy" to listOf(receiverUid)
                ),
                SetOptions.merge()
            )
        }
            .addOnSuccessListener {
                if (receiverUid.isNotBlank()) {
                    val notifRef = firestore.collection("notifications").document()
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to receiverUid,
                        "senderUid" to senderUid,
                        "type" to "NEW_MESSAGE",
                        "title" to "New Message",
                        "message" to "${senderName.ifBlank { "Someone" }} sent you a message.",
                        "relatedId" to conversationId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not send message.") }
    }

    override fun getMessages(
        conversationId: String,
        onSuccess: (List<FirestoreMessage>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("messages")
            .whereEqualTo("conversationId", conversationId)
            .get()
            .addOnSuccessListener { result ->
                val messages = result.documents.mapNotNull { docToMessage(it) }
                    .sortedBy { it.createdAt?.seconds ?: 0L }
                onSuccess(messages)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load messages.") }
    }

    override fun getUserConversations(
        uid: String,
        onSuccess: (List<FirestoreConversation>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .get()
            .addOnSuccessListener { result ->
                val convs = result.documents.mapNotNull { docToConversation(it) }
                    .sortedByDescending { it.lastMessageAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                onSuccess(convs)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load conversations.") }
    }

    override fun markConversationRead(
        conversationId: String,
        uid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("conversations").document(conversationId)
            .update("unreadBy", FieldValue.arrayRemove(uid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not update conversation.") }
    }

    override fun normalizeProgressConversation(
        conversationId: String,
        internshipTitle: String,
        onDone: () -> Unit
    ) {
        if (internshipTitle.isBlank()) { onDone(); return }
        val ref = firestore.collection("conversations").document(conversationId)
        ref.get()
            .addOnSuccessListener { doc ->
                val existing = doc.getString("internshipTitle") ?: ""
                if (existing.isNotBlank()) { onDone(); return@addOnSuccessListener } // already named
                ref.set(
                    mapOf(
                        "internshipTitle" to internshipTitle,
                        "title" to "$internshipTitle - Internship Chat",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { onDone() }
            }
            .addOnFailureListener { onDone() }
    }

    // ── Documents (metadata only — no Storage) ────────────────────────────────

    override fun recordDocumentSelection(
        document: FirestoreDocumentMeta,
        onSuccess: (documentId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("documents").document()
        val data = hashMapOf(
            "documentId" to ref.id,
            "ownerUid" to document.ownerUid,
            "relatedToType" to document.relatedToType,
            "relatedToId" to document.relatedToId,
            "documentName" to document.documentName,
            "documentType" to document.documentType,
            "selectedOnly" to true,
            "storageUrl" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(data)
            .addOnSuccessListener { onSuccess(ref.id) }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not record document.") }
    }

    override fun getDocumentsFor(
        relatedToId: String,
        onSuccess: (List<FirestoreDocumentMeta>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("documents")
            .whereEqualTo("relatedToId", relatedToId)
            .get()
            .addOnSuccessListener { result ->
                val docs = result.documents.mapNotNull { docToDocumentMeta(it) }
                onSuccess(docs)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load documents.") }
    }

    // ── Offers by company ─────────────────────────────────────────────────────

    override fun getOffersByCompany(
        companyId: String,
        onSuccess: (List<FirestoreInternshipOffer>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers")
            .whereEqualTo("companyId", companyId)
            .get()
            .addOnSuccessListener { result ->
                val open = result.documents.mapNotNull { docToOffer(it) }
                    .filter { it.status == "OPEN" }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(open)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load offers.") }
    }

    // ── Student instructor requests ───────────────────────────────────────────

    override fun getStudentInstructorRequests(
        studentUid: String,
        onSuccess: (List<FirestoreInstructorRequest>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("instructorRequests")
            .whereEqualTo("studentUid", studentUid)
            .get()
            .addOnSuccessListener { result ->
                val requests = result.documents.mapNotNull { docToRequest(it) }
                onSuccess(requests)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load requests.") }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    override fun getNotifications(
        recipientUid: String,
        onSuccess: (List<FirestoreNotification>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("notifications")
            .whereEqualTo("recipientUid", recipientUid)
            .get()
            .addOnSuccessListener { result ->
                val sorted = result.documents.mapNotNull { docToNotification(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(sorted)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load notifications.") }
    }

    override fun markNotificationRead(
        notificationId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("notifications").document(notificationId)
            .set(mapOf("isRead" to true), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not update notification.") }
    }

    override fun hasUnreadNotifications(
        recipientUid: String,
        onResult: (Boolean) -> Unit
    ) {
        firestore.collection("notifications")
            .whereEqualTo("recipientUid", recipientUid)
            .get()
            .addOnSuccessListener { result ->
                val hasUnread = result.documents.any { doc -> doc.getBoolean("isRead") == false }
                onResult(hasUnread)
            }
            .addOnFailureListener { onResult(false) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun createUserDocument(
        uid: String,
        user: User,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val doc = hashMapOf(
            "uid" to uid,
            "email" to user.email,
            "fullName" to user.fullName,
            "role" to user.role,
            "accountStatus" to user.accountStatus,
            "verifiedAt" to null,
            "verifiedBy" to null,
            "rejectionReason" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(uid)
            .set(doc)
            .addOnSuccessListener {
                // Do NOT notify admins here — the user hasn't submitted verification yet, and the
                // still-PENDING account cannot legally show up as an approvable request. Notification
                // fan-out fires after the verification screen saves successfully (see
                // notifyAdminsOfVerificationSubmitted, called from each verification activity).
                onSuccess(user)
            }
            .addOnFailureListener {
                auth.signOut()
                onFailure(
                    "Account created but profile setup failed. " +
                    "Please check your connection and try again."
                )
            }
    }

    /**
     * Public entry: fan-out an ADMIN_ACCOUNT_REQUEST to every ACTIVE admin, called from each
     * verification screen once the user finishes submitting proof. Best-effort — failures are
     * swallowed. Title is role-specific so the Admin Notifications list is self-explanatory.
     */
    override fun notifyAdminsOfVerificationSubmitted(
        newUserUid: String,
        fullName: String,
        role: String
    ) {
        val (title, roleLabel) = when (role) {
            UserRole.STUDENT.value -> "New student verification request" to "Student"
            UserRole.INSTRUCTOR.value -> "New instructor verification request" to "Instructor"
            UserRole.COMPANY_SUPERVISOR.value -> "New company supervisor verification request" to "Company Supervisor"
            else -> "New account verification request" to "User"
        }
        firestore.collection("users")
            .whereEqualTo("role", UserRole.ADMIN.value)
            .whereEqualTo("accountStatus", AccountStatus.ACTIVE.value)
            .get()
            .addOnSuccessListener { result ->
                for (adminDoc in result.documents) {
                    val adminUid = adminDoc.getString("uid") ?: adminDoc.id
                    val ref = firestore.collection("notifications").document()
                    ref.set(hashMapOf(
                        "notificationId" to ref.id,
                        "recipientUid" to adminUid,
                        "recipientRole" to "ADMIN",
                        "senderUid" to newUserUid,
                        "type" to "ADMIN_ACCOUNT_REQUEST",
                        "title" to title,
                        "message" to "${fullName.ifBlank { "A new user" }} submitted a $roleLabel verification request.",
                        "relatedId" to newUserUid,
                        "relatedUserId" to newUserUid,
                        "relatedUserRole" to role,
                        "targetScreen" to "PENDING_REQUESTS",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
            }
            .addOnFailureListener { /* best-effort — ignore */ }
    }

    /** Notify a single user (best-effort). Used for approve/reject account decisions. */
    private fun notifyUser(
        recipientUid: String,
        type: String,
        title: String,
        message: String
    ) {
        if (recipientUid.isBlank()) return
        val ref = firestore.collection("notifications").document()
        ref.set(hashMapOf(
            "notificationId" to ref.id,
            "recipientUid" to recipientUid,
            "senderUid" to (auth.currentUser?.uid ?: ""),
            "type" to type,
            "title" to title,
            "message" to message,
            "relatedId" to recipientUid,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        ))
    }

    // ── University change requests ─────────────────────────────────────────────

    override fun createUniversityChangeRequest(
        request: com.example.intertrack.data.model.FirestoreUniversityChangeRequest,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("universityChangeRequests").document()
        val doc = hashMapOf(
            "requestId" to ref.id,
            "userUid" to request.userUid,
            "userRole" to request.userRole,
            "userName" to request.userName,
            "userEmail" to request.userEmail,
            "currentUniversity" to request.currentUniversity,
            "currentUniversityKey" to request.currentUniversityKey,
            "requestedUniversity" to request.requestedUniversity,
            "requestedUniversityKey" to request.requestedUniversityKey,
            "reason" to request.reason,
            "proofFileName" to request.proofFileName,
            "proofMimeType" to request.proofMimeType,
            "proofUrl" to request.proofUrl,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "reviewedAt" to null,
            "reviewedByAdminUid" to "",
            "rejectionReason" to ""
        )
        ref.set(doc)
            .addOnSuccessListener {
                notifyAdminsOfUniversityChange(request)
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not submit request.") }
    }

    /** Notify all ACTIVE admins that a university change request was submitted. Best-effort. */
    private fun notifyAdminsOfUniversityChange(
        request: com.example.intertrack.data.model.FirestoreUniversityChangeRequest
    ) {
        firestore.collection("users")
            .whereEqualTo("role", UserRole.ADMIN.value)
            .whereEqualTo("accountStatus", AccountStatus.ACTIVE.value)
            .get()
            .addOnSuccessListener { result ->
                for (adminDoc in result.documents) {
                    val adminUid = adminDoc.getString("uid") ?: adminDoc.id
                    val ref = firestore.collection("notifications").document()
                    ref.set(hashMapOf(
                        "notificationId" to ref.id,
                        "recipientUid" to adminUid,
                        "recipientRole" to "ADMIN",
                        "senderUid" to request.userUid,
                        "type" to "UNIVERSITY_CHANGE_REQUEST",
                        "title" to "University change request",
                        "message" to "${request.userName.ifBlank { "A user" }} requested to change university from " +
                            "${request.currentUniversity.ifBlank { "—" }} to ${request.requestedUniversity}.",
                        "relatedId" to request.userUid,
                        "relatedUserId" to request.userUid,
                        "relatedUserRole" to request.userRole,
                        "targetScreen" to "UNIVERSITY_CHANGE_REQUESTS",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
            }
            .addOnFailureListener { /* best-effort */ }
    }

    override fun getPendingUniversityChangeRequest(
        userUid: String,
        onSuccess: (com.example.intertrack.data.model.FirestoreUniversityChangeRequest?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Equality-only query (no composite index); newest PENDING chosen client-side.
        firestore.collection("universityChangeRequests")
            .whereEqualTo("userUid", userUid)
            .get()
            .addOnSuccessListener { result ->
                val pending = result.documents.mapNotNull { docToUniversityChangeRequest(it) }
                    .filter { it.status == "PENDING" }
                    .maxByOrNull { it.createdAt?.seconds ?: 0L }
                onSuccess(pending)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not check requests.") }
    }

    override fun getUniversityChangeRequests(
        onSuccess: (List<com.example.intertrack.data.model.FirestoreUniversityChangeRequest>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("universityChangeRequests")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { docToUniversityChangeRequest(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load requests.") }
    }

    override fun approveUniversityChangeRequest(
        request: com.example.intertrack.data.model.FirestoreUniversityChangeRequest,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val reqRef = firestore.collection("universityChangeRequests").document(request.requestId)
        // 1) Mark the request approved.
        reqRef.set(mapOf(
            "status" to "APPROVED",
            "reviewedAt" to FieldValue.serverTimestamp(),
            "reviewedByAdminUid" to adminUid
        ), SetOptions.merge())
            .addOnSuccessListener {
                // 2) Apply the university change to the user document (this is the ONLY place it moves).
                firestore.collection("users").document(request.userUid)
                    .set(mapOf(
                        "university" to request.requestedUniversity,
                        "universityKey" to request.requestedUniversityKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ), SetOptions.merge())
                    .addOnSuccessListener {
                        notifyUser(request.userUid, "UNIVERSITY_CHANGE_APPROVED",
                            "University Change Approved",
                            "Your university change request has been approved. Your university is now " +
                                "${request.requestedUniversity}.")
                        onSuccess()
                    }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not update the user's university.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not approve the request.") }
    }

    override fun rejectUniversityChangeRequest(
        requestId: String,
        userUid: String,
        adminUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Request-only update — the user's university is left unchanged.
        firestore.collection("universityChangeRequests").document(requestId)
            .set(mapOf(
                "status" to "REJECTED",
                "rejectionReason" to reason,
                "reviewedAt" to FieldValue.serverTimestamp(),
                "reviewedByAdminUid" to adminUid
            ), SetOptions.merge())
            .addOnSuccessListener {
                val suffix = if (reason.isNotBlank()) " Reason: $reason" else ""
                notifyUser(userUid, "UNIVERSITY_CHANGE_REJECTED", "University Change Rejected",
                    "Your university change request was rejected.$suffix")
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not reject the request.") }
    }

    private fun docToUniversityChangeRequest(
        doc: DocumentSnapshot
    ): com.example.intertrack.data.model.FirestoreUniversityChangeRequest? {
        return try {
            com.example.intertrack.data.model.FirestoreUniversityChangeRequest(
                requestId = doc.getString("requestId") ?: doc.id,
                userUid = doc.getString("userUid") ?: "",
                userRole = doc.getString("userRole") ?: "",
                userName = doc.getString("userName") ?: "",
                userEmail = doc.getString("userEmail") ?: "",
                currentUniversity = doc.getString("currentUniversity") ?: "",
                currentUniversityKey = doc.getString("currentUniversityKey") ?: "",
                requestedUniversity = doc.getString("requestedUniversity") ?: "",
                requestedUniversityKey = doc.getString("requestedUniversityKey") ?: "",
                reason = doc.getString("reason") ?: "",
                proofFileName = doc.getString("proofFileName") ?: "",
                proofMimeType = doc.getString("proofMimeType") ?: "",
                proofUrl = doc.getString("proofUrl"),
                status = doc.getString("status") ?: "PENDING",
                createdAt = doc.getTimestamp("createdAt"),
                reviewedAt = doc.getTimestamp("reviewedAt"),
                reviewedByAdminUid = doc.getString("reviewedByAdminUid") ?: "",
                rejectionReason = doc.getString("rejectionReason") ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun fetchAndValidateUser(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    auth.signOut()
                    onFailure("Account profile not found. Please register again.")
                    return@addOnSuccessListener
                }

                val roleStr = document.getString("role")
                val statusStr = document.getString("accountStatus")
                val userRole = UserRole.fromString(roleStr)
                val accountStatus = AccountStatus.fromString(statusStr)

                if (userRole == null) {
                    auth.signOut()
                    onFailure("Invalid account role. Please contact support.")
                    return@addOnSuccessListener
                }

                if (accountStatus == null) {
                    auth.signOut()
                    onFailure("Invalid account status. Please contact support.")
                    return@addOnSuccessListener
                }

                if (userRole == UserRole.ADMIN && accountStatus != AccountStatus.ACTIVE) {
                    auth.signOut()
                    onFailure("Administrator account is not active. Please contact support.")
                    return@addOnSuccessListener
                }

                onSuccess(
                    User(
                        uid = uid,
                        email = document.getString("email") ?: "",
                        fullName = document.getString("fullName") ?: "",
                        role = userRole.value,
                        accountStatus = accountStatus.value,
                        verifiedBy = document.getString("verifiedBy"),
                        rejectionReason = document.getString("rejectionReason"),
                        university = document.getString("university"),
                        universityKey = document.getString("universityKey"),
                        department = document.getString("department"),
                        major = document.getString("major"),
                        academicYear = document.getString("academicYear"),
                        bio = document.getString("bio"),
                        office = document.getString("office"),
                        companyId = document.getString("companyId"),
                        position = document.getString("position"),
                        assignedInstructorUid = document.getString("assignedInstructorUid"),
                        assignedInstructorName = document.getString("assignedInstructorName")
                    )
                )
            }
            .addOnFailureListener {
                onFailure("Could not load your account. Check your internet connection.")
            }
    }

    private fun docToOffer(doc: DocumentSnapshot): FirestoreInternshipOffer? {
        return try {
            FirestoreInternshipOffer(
                offerId = doc.getString("offerId") ?: doc.id,
                supervisorUid = doc.getString("supervisorUid") ?: "",
                companyId = doc.getString("companyId") ?: "",
                companyName = doc.getString("companyName") ?: "",
                supervisorName = doc.getString("supervisorName") ?: "",
                title = doc.getString("title") ?: "",
                department = doc.getString("department") ?: "",
                description = doc.getString("description") ?: "",
                requirements = doc.getString("requirements") ?: "",
                duration = doc.getString("duration") ?: "",
                seats = (doc.getLong("seats") ?: 0L).toInt(),
                status = doc.getString("status") ?: "OPEN",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
        } catch (_: Exception) { null }
    }

    private fun docToApplication(doc: DocumentSnapshot): FirestoreInternshipApplication? {
        return try {
            FirestoreInternshipApplication(
                applicationId = doc.getString("applicationId") ?: doc.id,
                studentUid = doc.getString("studentUid") ?: "",
                studentName = doc.getString("studentName") ?: "",
                studentEmail = doc.getString("studentEmail") ?: "",
                studentUniversity = doc.getString("studentUniversity") ?: "",
                studentMajor = doc.getString("studentMajor") ?: "",
                studentYearLevel = doc.getString("studentYearLevel") ?: "",
                studentPhone = doc.getString("studentPhone") ?: "",
                studentGpa = doc.getString("studentGpa") ?: "",
                studentSkills = doc.getString("studentSkills") ?: "",
                motivation = doc.getString("motivation") ?: "",
                startDate = doc.getString("startDate") ?: "",
                duration = doc.getString("duration") ?: "",
                preferredDepartment = doc.getString("preferredDepartment") ?: "",
                previousExperience = doc.getString("previousExperience") ?: "",
                portfolioLink = doc.getString("portfolioLink") ?: "",
                companyId = doc.getString("companyId") ?: "",
                companyName = doc.getString("companyName") ?: "",
                supervisorUid = doc.getString("supervisorUid") ?: "",
                offerId = doc.getString("offerId") ?: "",
                offerTitle = doc.getString("offerTitle") ?: "",
                assignedInstructorUid = doc.getString("assignedInstructorUid") ?: "",
                assignedInstructorName = doc.getString("assignedInstructorName") ?: "",
                status = doc.getString("status") ?: "PENDING",
                rejectionReason = doc.getString("rejectionReason") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt"),
                reviewedAt = doc.getTimestamp("reviewedAt"),
                reviewedByUid = doc.getString("reviewedByUid") ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun docToRequest(doc: DocumentSnapshot): FirestoreInstructorRequest? {
        return try {
            FirestoreInstructorRequest(
                requestId = doc.getString("requestId") ?: doc.id,
                studentUid = doc.getString("studentUid") ?: "",
                studentName = doc.getString("studentName") ?: "",
                studentEmail = doc.getString("studentEmail") ?: "",
                studentUniversity = doc.getString("studentUniversity") ?: "",
                studentMajor = doc.getString("studentMajor") ?: "",
                studentGpa = doc.getString("studentGpa") ?: "",
                studentCompanyName = doc.getString("studentCompanyName") ?: "",
                instructorUid = doc.getString("instructorUid") ?: "",
                instructorName = doc.getString("instructorName") ?: "",
                instructorEmail = doc.getString("instructorEmail") ?: "",
                status = doc.getString("status") ?: "PENDING",
                rejectionReason = doc.getString("rejectionReason") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt"),
                reviewedAt = doc.getTimestamp("reviewedAt"),
                reviewedByUid = doc.getString("reviewedByUid") ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun docToMessage(doc: DocumentSnapshot): FirestoreMessage? {
        return try {
            FirestoreMessage(
                messageId = doc.getString("messageId") ?: doc.id,
                conversationId = doc.getString("conversationId") ?: "",
                senderUid = doc.getString("senderUid") ?: "",
                senderName = doc.getString("senderName") ?: "",
                receiverUid = doc.getString("receiverUid") ?: "",
                messageText = doc.getString("messageText") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                isRead = doc.getBoolean("isRead") ?: false
            )
        } catch (_: Exception) { null }
    }

    private fun docToConversation(doc: DocumentSnapshot): FirestoreConversation? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val participantUids = (doc.get("participantUids") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val participantNames = (doc.get("participantNames") as? Map<String, String>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val unreadBy = (doc.get("unreadBy") as? List<String>) ?: emptyList()
            FirestoreConversation(
                conversationId = doc.getString("conversationId") ?: doc.id,
                participantUids = participantUids,
                participantNames = participantNames,
                lastMessage = doc.getString("lastMessage") ?: "",
                lastMessageAt = doc.getTimestamp("lastMessageAt"),
                unreadBy = unreadBy,
                type = doc.getString("type") ?: "",
                connectionId = doc.getString("connectionId") ?: "",
                internshipTitle = doc.getString("internshipTitle") ?: "",
                title = doc.getString("title") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
        } catch (_: Exception) { null }
    }

    private fun docToDocumentMeta(doc: DocumentSnapshot): FirestoreDocumentMeta? {
        return try {
            FirestoreDocumentMeta(
                documentId = doc.getString("documentId") ?: doc.id,
                ownerUid = doc.getString("ownerUid") ?: "",
                relatedToType = doc.getString("relatedToType") ?: "",
                relatedToId = doc.getString("relatedToId") ?: "",
                documentName = doc.getString("documentName") ?: "",
                documentType = doc.getString("documentType") ?: "",
                selectedOnly = doc.getBoolean("selectedOnly") ?: true,
                storageUrl = doc.getString("storageUrl"),
                createdAt = doc.getTimestamp("createdAt")
            )
        } catch (_: Exception) { null }
    }

    private fun docToReport(doc: DocumentSnapshot): FirestoreReport? {
        return try {
            FirestoreReport(
                reportId = doc.getString("reportId") ?: doc.id,
                internshipConnectionId = doc.getString("internshipConnectionId") ?: "",
                studentUid = doc.getString("studentUid") ?: "",
                studentName = doc.getString("studentName") ?: "",
                companyId = doc.getString("companyId") ?: "",
                companyName = doc.getString("companyName") ?: "",
                internshipId = doc.getString("internshipId") ?: "",
                internshipTitle = doc.getString("internshipTitle") ?: "",
                instructorUid = doc.getString("instructorUid") ?: "",
                supervisorUid = doc.getString("supervisorUid") ?: "",
                reportTitle = doc.getString("reportTitle") ?: "",
                reportPeriod = doc.getString("reportPeriod") ?: "",
                reportContent = doc.getString("reportContent") ?: "",
                challenges = doc.getString("challenges") ?: "",
                learnedSkills = doc.getString("learnedSkills") ?: "",
                hoursWorked = doc.getString("hoursWorked") ?: "",
                status = doc.getString("status") ?: "SUBMITTED",
                attachedFileName = doc.getString("attachedFileName") ?: "",
                attachedFileMimeType = doc.getString("attachedFileMimeType") ?: "",
                downloadUrl = doc.getString("downloadUrl") ?: "",
                storageUrl = doc.getString("storageUrl") ?: "",
                reportWeekNumber = (doc.getLong("reportWeekNumber") ?: 0L).toInt(),
                periodStart = doc.getTimestamp("periodStart"),
                periodEnd = doc.getTimestamp("periodEnd"),
                deadlineDate = doc.getTimestamp("deadlineDate"),
                submittedAt = doc.getTimestamp("submittedAt"),
                instructorFeedback = doc.getString("instructorFeedback") ?: "",
                supervisorFeedback = doc.getString("supervisorFeedback") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt"),
                reviewedAt = doc.getTimestamp("reviewedAt")
            )
        } catch (_: Exception) { null }
    }

    private fun docToNotification(doc: DocumentSnapshot): FirestoreNotification? {
        return try {
            FirestoreNotification(
                notificationId = doc.getString("notificationId") ?: doc.id,
                recipientUid = doc.getString("recipientUid") ?: "",
                recipientRole = doc.getString("recipientRole") ?: "",
                senderUid = doc.getString("senderUid") ?: "",
                type = doc.getString("type") ?: "",
                title = doc.getString("title") ?: "",
                message = doc.getString("message") ?: "",
                relatedId = doc.getString("relatedId") ?: "",
                relatedCompanyId = doc.getString("relatedCompanyId") ?: "",
                relatedInternshipId = doc.getString("relatedInternshipId") ?: "",
                relatedUserId = doc.getString("relatedUserId") ?: "",
                relatedUserRole = doc.getString("relatedUserRole") ?: "",
                targetScreen = doc.getString("targetScreen") ?: "",
                isRead = doc.getBoolean("isRead") ?: false,
                createdAt = doc.getTimestamp("createdAt")
            )
        } catch (_: Exception) { null }
    }

    // ── Companies with open offers ────────────────────────────────────────────

    override fun fetchCompaniesWithOpenOffers(
        onSuccess: (List<FirestoreCompany>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers")
            .whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener { offersResult ->
                val companyIds = offersResult.documents
                    .mapNotNull { it.getString("companyId") }
                    .filter { it.isNotBlank() }
                    .distinct()
                Log.d(
                    "ExploreOffers",
                    "fetchCompaniesWithOpenOffers — collection=internshipOffers status=OPEN " +
                        "openOffers=${offersResult.size()} companyIds=$companyIds"
                )
                if (companyIds.isEmpty()) {
                    // Query succeeded but no OPEN offers reference a company — true empty result.
                    onSuccess(emptyList())
                    return@addOnSuccessListener
                }
                // Company docs store their id ONLY as the Firestore document id — there is no
                // "companyId" field inside the document. So we must look each one up with
                // .document(companyId), NOT whereEqualTo/whereIn on a "companyId" field
                // (that always matched zero docs, which is why students saw "No companies").
                val companies = mutableListOf<FirestoreCompany>()
                var pending = companyIds.size
                for (companyId in companyIds) {
                    firestore.collection("companies").document(companyId)
                        .get()
                        .addOnSuccessListener { compDoc ->
                            if (compDoc.exists()) {
                                docToCompany(compDoc)?.let { companies.add(it) }
                            } else {
                                // Don't fail the whole screen for one dangling reference.
                                Log.w(
                                    "ExploreOffers",
                                    "OPEN offer references a missing company doc: companyId=$companyId"
                                )
                            }
                            pending--
                            if (pending == 0) finishCompaniesWithOpenOffers(companies, onSuccess)
                        }
                        .addOnFailureListener { e ->
                            Log.e(
                                "ExploreOffers",
                                "Failed to load company doc companyId=$companyId: ${e.message}"
                            )
                            pending--
                            if (pending == 0) finishCompaniesWithOpenOffers(companies, onSuccess)
                        }
                }
            }
            .addOnFailureListener { e ->
                // Surface as a real error so the UI shows retry instead of a false "empty" state.
                Log.e("ExploreOffers", "fetchCompaniesWithOpenOffers offers query failed: ${e.message}")
                onFailure(e.message ?: "Could not load companies.")
            }
    }

    private fun finishCompaniesWithOpenOffers(
        companies: List<FirestoreCompany>,
        onSuccess: (List<FirestoreCompany>) -> Unit
    ) {
        val result = companies
            .distinctBy { it.companyId }
            .sortedBy { it.name.lowercase() }
        Log.d("ExploreOffers", "Loaded ${result.size} companies with OPEN offers")
        onSuccess(result)
    }

    // ── Open offers with their companies (offer-first Explore) ────────────────

    override fun fetchOpenOffersWithCompanies(
        onSuccess: (List<com.example.intertrack.data.model.ExploreOffer>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipOffers")
            .whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener { offersResult ->
                // Dedupe by offer document id, newest OPEN first.
                val offers = offersResult.documents
                    .mapNotNull { docToOffer(it) }
                    .distinctBy { it.offerId }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                Log.d(
                    "ExploreOffers",
                    "fetchOpenOffersWithCompanies — collection=internshipOffers status=OPEN " +
                        "openOffers=${offers.size}"
                )
                if (offers.isEmpty()) {
                    onSuccess(emptyList())
                    return@addOnSuccessListener
                }

                val companyIds = offers.map { it.companyId }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (companyIds.isEmpty()) {
                    // Offers exist but carry no companyId — still show them (no company enrichment).
                    onSuccess(offers.map {
                        com.example.intertrack.data.model.ExploreOffer(it, null)
                    })
                    return@addOnSuccessListener
                }

                // Load each referenced company ONCE by its document id (companies have no
                // "companyId" field — see fetchCompaniesWithOpenOffers). Cache → no N+1.
                val companyCache = HashMap<String, FirestoreCompany>()
                var pending = companyIds.size
                for (companyId in companyIds) {
                    firestore.collection("companies").document(companyId)
                        .get()
                        .addOnSuccessListener { compDoc ->
                            if (compDoc.exists()) {
                                docToCompany(compDoc)?.let { companyCache[companyId] = it }
                            } else {
                                Log.w("ExploreOffers", "OPEN offer references missing company: $companyId")
                            }
                            pending--
                            if (pending == 0) {
                                onSuccess(offers.map {
                                    com.example.intertrack.data.model.ExploreOffer(
                                        it, companyCache[it.companyId]
                                    )
                                })
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ExploreOffers", "Failed to load company $companyId: ${e.message}")
                            pending--
                            if (pending == 0) {
                                onSuccess(offers.map {
                                    com.example.intertrack.data.model.ExploreOffer(
                                        it, companyCache[it.companyId]
                                    )
                                })
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ExploreOffers", "fetchOpenOffersWithCompanies offers query failed: ${e.message}")
                onFailure(e.message ?: "Could not load internships.")
            }
    }

    // ── Password change ────────────────────────────────────────────────────────

    override fun changePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onFailure("You must be logged in to change your password.")
            return
        }
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(mapAuthError(e)) }
            }
            .addOnFailureListener { e -> onFailure(mapAuthError(e)) }
    }

    // ── Internship connections ─────────────────────────────────────────────────

    override fun getStudentActiveConnection(
        studentUid: String,
        onSuccess: (FirestoreInternshipConnection?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections")
            .whereEqualTo("studentUid", studentUid)
            .whereIn("status", listOf(
                "ACTIVE", "WAITING_INSTRUCTOR_CONNECTION", "COMPLETED", "ENDED_BY_COMPANY", "ENDED"))
            .get()
            .addOnSuccessListener { result ->
                // Prefer an in-progress internship (ACTIVE > WAITING); otherwise surface the most
                // recent COMPLETED one so the student sees their completion/congratulations; failing
                // that, the most recent company-ended one so they see the ended notice.
                val docs = result.documents.mapNotNull { docToConnection(it) }
                val conn = docs.firstOrNull { it.status == "ACTIVE" }
                    ?: docs.firstOrNull { it.status == "WAITING_INSTRUCTOR_CONNECTION" }
                    ?: docs.filter { it.status == "COMPLETED" }
                        .maxByOrNull { it.completedAt?.seconds ?: 0L }
                    ?: docs.filter { it.isEndedByCompany() }
                        .maxByOrNull { it.endedAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                    ?: docs.firstOrNull()
                onSuccess(conn)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load connection.") }
    }

    override fun getCompanyConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections")
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { result ->
                val conns = result.documents.mapNotNull { docToConnection(it) }
                onSuccess(conns)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load connections.") }
    }

    override fun getCompanyAllConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // The Review list must include newly-accepted internships — those live in
        // WAITING_INSTRUCTOR_CONNECTION until the instructor confirms — otherwise the student
        // vanishes from Company Review right after acceptance. Also query by supervisorUid as a
        // fallback so a connection whose companyId is blank/stale still surfaces for the right
        // supervisor (Firestore rules already permit both reads for the owning supervisor).
        val col = firestore.collection("internshipConnections")
        val statuses = listOf(
            "WAITING_INSTRUCTOR_CONNECTION",
            "ACTIVE",
            "COMPLETED",
            "ENDED_BY_COMPANY",
            "ENDED"
        )
        val supervisorUid = auth.currentUser?.uid ?: ""

        val byCompany = col.whereEqualTo("companyId", companyId)
            .whereIn("status", statuses).get()
        val bySupervisor = if (supervisorUid.isNotBlank()) {
            col.whereEqualTo("supervisorUid", supervisorUid)
                .whereIn("status", statuses).get()
        } else null

        byCompany
            .addOnSuccessListener { companyResult ->
                fun finish(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
                    val conns = docs.mapNotNull { docToConnection(it) }
                        .distinctBy { it.connectionId }
                        .sortedByDescending {
                            it.completedAt?.seconds ?: it.endedAt?.seconds
                                ?: it.updatedAt?.seconds ?: it.startedAt?.seconds ?: 0L
                        }
                    onSuccess(conns)
                }
                if (bySupervisor == null) {
                    finish(companyResult.documents)
                } else {
                    bySupervisor
                        .addOnSuccessListener { supResult ->
                            finish(companyResult.documents + supResult.documents)
                        }
                        .addOnFailureListener { finish(companyResult.documents) }
                }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load internships.") }
    }

    override fun endInternshipConnection(
        connectionId: String,
        studentUid: String,
        instructorUid: String,
        reason: String,
        companyId: String,
        companyName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val trimmedReason = reason.trim()
        // Status update only — keeps studentUid/companyId unchanged so the supervisor rule passes.
        // Merge preserves the document for history (no hard delete).
        firestore.collection("internshipConnections").document(connectionId)
            .set(mapOf(
                "status" to "ENDED_BY_COMPANY",
                "endedBy" to "company",
                "endReason" to trimmedReason,
                "endedByCompanyId" to companyId,
                "endedByCompanyName" to companyName,
                "endedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())
            .addOnSuccessListener {
                val reasonSuffix = if (trimmedReason.isNotBlank()) " Reason: $trimmedReason" else ""
                if (studentUid.isNotBlank()) {
                    val notifRef = firestore.collection("notifications").document()
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to studentUid,
                        "senderUid" to (auth.currentUser?.uid ?: ""),
                        "type" to "INTERNSHIP_ENDED",
                        "title" to "Internship Ended",
                        "message" to "Your internship was ended by the company.$reasonSuffix",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to companyId,
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                if (instructorUid.isNotBlank()) {
                    val notifRef = firestore.collection("notifications").document()
                    notifRef.set(hashMapOf(
                        "notificationId" to notifRef.id,
                        "recipientUid" to instructorUid,
                        "senderUid" to (auth.currentUser?.uid ?: ""),
                        "type" to "INTERNSHIP_ENDED",
                        "title" to "Internship Ended",
                        "message" to "An internship you supervise was ended by the company.$reasonSuffix",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to companyId,
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not end internship.") }
    }

    override fun endInternshipByInstructor(
        connectionId: String,
        studentUid: String,
        instructorUid: String,
        supervisorUid: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val trimmed = reason.trim()
        // Status update only — mirrors endInternshipConnection but with an instructor-specific
        // status so the audit is clear. Firestore rule permits instructors to update connections
        // they supervise; studentUid/companyId are preserved so the doc keeps its identity.
        firestore.collection("internshipConnections").document(connectionId)
            .set(mapOf(
                "status" to "ENDED_BY_INSTRUCTOR",
                "endedBy" to "instructor",
                "endReason" to trimmed,
                "removedByInstructorUid" to instructorUid,
                "endedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())
            .addOnSuccessListener {
                val reasonSuffix = if (trimmed.isNotBlank()) " Reason: $trimmed" else ""
                if (studentUid.isNotBlank()) {
                    val ref = firestore.collection("notifications").document()
                    ref.set(hashMapOf(
                        "notificationId" to ref.id,
                        "recipientUid" to studentUid,
                        "senderUid" to (auth.currentUser?.uid ?: instructorUid),
                        "type" to "INTERNSHIP_ENDED",
                        "title" to "Internship Cancelled",
                        "message" to
                            "Your instructor removed you from supervision. Your internship was cancelled.$reasonSuffix",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                if (supervisorUid.isNotBlank()) {
                    val ref = firestore.collection("notifications").document()
                    ref.set(hashMapOf(
                        "notificationId" to ref.id,
                        "recipientUid" to supervisorUid,
                        "senderUid" to (auth.currentUser?.uid ?: instructorUid),
                        "type" to "INTERNSHIP_ENDED",
                        "title" to "Internship Cancelled",
                        "message" to
                            "The instructor removed the student from supervision. The internship was cancelled.$reasonSuffix",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not end internship.") }
    }

    override fun cancelApplication(
        applicationId: String,
        studentUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipApplications").document(applicationId)
            .set(mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not cancel application.") }
    }

    // ── Progress group chat ────────────────────────────────────────────────────

    override fun getOrCreateProgressConversation(
        connectionId: String,
        studentUid: String,
        studentName: String,
        supervisorUid: String,
        supervisorName: String,
        instructorUid: String,
        instructorName: String,
        onSuccess: (conversationId: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Gate: connection must be ACTIVE before a progress chat can exist
        firestore.collection("internshipConnections").document(connectionId)
            .get()
            .addOnSuccessListener { connDoc ->
                val connStatus = if (connDoc.exists()) connDoc.getString("status") ?: "" else ""
                if (connStatus != "ACTIVE") {
                    onFailure("Progress chat will be available after your instructor connects you with the company.")
                    return@addOnSuccessListener
                }
                val convId = "progress_$connectionId"
                val ref = firestore.collection("conversations").document(convId)
                ref.get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            onSuccess(convId)
                            return@addOnSuccessListener
                        }
                        val participants = mutableListOf(studentUid, supervisorUid)
                        val names = mutableMapOf(studentUid to studentName, supervisorUid to supervisorName)
                        if (instructorUid.isNotBlank()) {
                            participants.add(instructorUid)
                            names[instructorUid] = instructorName
                        }
                        val data = hashMapOf(
                            "conversationId" to convId,
                            "participantUids" to participants,
                            "participantNames" to names,
                            "lastMessage" to "",
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "unreadBy" to emptyList<String>(),
                            "type" to "INTERNSHIP_PROGRESS",
                            "connectionId" to connectionId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        ref.set(data)
                            .addOnSuccessListener { onSuccess(convId) }
                            .addOnFailureListener { e -> onFailure(e.message ?: "Could not create progress chat.") }
                    }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not start progress chat.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not verify connection status.") }
    }

    override fun getConnectionById(
        connectionId: String,
        onSuccess: (FirestoreInternshipConnection?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections").document(connectionId)
            .get()
            .addOnSuccessListener { doc ->
                onSuccess(if (doc.exists()) docToConnection(doc) else null)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load connection.") }
    }

    override fun createInternshipConnection(
        connection: FirestoreInternshipConnection,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ref = firestore.collection("internshipConnections").document(connection.connectionId)
        ref.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) { onSuccess(); return@addOnSuccessListener }
                val data = hashMapOf(
                    "connectionId" to connection.connectionId,
                    "applicationId" to connection.applicationId,
                    "studentUid" to connection.studentUid,
                    "studentName" to connection.studentName,
                    "companyId" to connection.companyId,
                    "companyName" to connection.companyName,
                    "supervisorUid" to connection.supervisorUid,
                    "supervisorName" to connection.supervisorName,
                    "internshipId" to connection.internshipId,
                    "internshipTitle" to connection.internshipTitle,
                    "instructorUid" to connection.instructorUid,
                    "instructorName" to connection.instructorName,
                    "status" to connection.status,
                    "connectedAt" to null,
                    "connectedByInstructorUid" to "",
                    "startedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(data)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e.message ?: "Could not create connection.") }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not check connection.") }
    }

    override fun connectInternship(
        connectionId: String,
        instructorUid: String,
        instructorName: String,
        studentUid: String,
        studentName: String,
        supervisorUid: String,
        supervisorName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val connRef = firestore.collection("internshipConnections").document(connectionId)
        val convRef = firestore.collection("conversations").document("progress_$connectionId")

        // Period dates are OWNED by the company and were set at accept time; the instructor only
        // copies/keeps them. Captured here (inside the txn) purely to enrich the connected notification.
        var periodStartMs: Long? = null
        var periodEndMs: Long? = null

        // Atomic activation: the connection flip to ACTIVE and the creation of the official
        // progress conversation happen in ONE transaction. If anything fails, the connection
        // stays WAITING_INSTRUCTOR_CONNECTION and no partial conversation is left behind.
        firestore.runTransaction<Void?> { txn ->
            val connSnap = txn.get(connRef)
            if (!connSnap.exists()) throw Exception("This internship connection no longer exists.")

            val status = connSnap.getString("status") ?: ""
            val cInstr = connSnap.getString("instructorUid") ?: ""
            val cStudent = connSnap.getString("studentUid") ?: ""
            val cSuper = connSnap.getString("supervisorUid") ?: ""
            val cInternship = connSnap.getString("internshipId") ?: ""
            periodStartMs = connSnap.getTimestamp("startDate")?.toDate()?.time
            periodEndMs = connSnap.getTimestamp("endDate")?.toDate()?.time

            // Validate before any write.
            if (status != "WAITING_INSTRUCTOR_CONNECTION")
                throw Exception("This connection is not waiting for confirmation.")
            if (cInstr.isNotBlank() && cInstr != instructorUid)
                throw Exception("You are not the assigned instructor for this internship.")
            if (cStudent.isBlank() || cSuper.isBlank() || cInternship.isBlank())
                throw Exception("This connection is missing required information.")

            // All reads must precede writes in a Firestore transaction.
            val convSnap = txn.get(convRef)

            // NOTE: the internship period (startDate/endDate/requiredReportsCount) is deliberately
            // NOT written here — it belongs to the company supervisor and is left untouched (copied).
            txn.update(
                connRef,
                mapOf(
                    "status" to "ACTIVE",
                    "instructorUid" to instructorUid,
                    "instructorName" to instructorName,
                    "connectedAt" to FieldValue.serverTimestamp(),
                    "connectedByInstructorUid" to instructorUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // Deterministic conversation id (progress_{connectionId}) → retries never duplicate.
            if (!convSnap.exists()) {
                val sName = connSnap.getString("studentName") ?: studentName
                val supName = connSnap.getString("supervisorName") ?: supervisorName
                val companyId = connSnap.getString("companyId") ?: ""
                val companyName = connSnap.getString("companyName") ?: ""
                val applicationId = connSnap.getString("applicationId") ?: ""
                val internshipTitle = connSnap.getString("internshipTitle") ?: ""
                val participants = listOf(cStudent, cSuper, instructorUid)
                val names = mapOf(
                    cStudent to sName,
                    cSuper to supName,
                    instructorUid to instructorName
                )
                txn.set(
                    convRef,
                    hashMapOf(
                        "conversationId" to convRef.id,
                        "type" to "INTERNSHIP_PROGRESS",
                        "connectionId" to connectionId,
                        "applicationId" to applicationId,
                        "internshipId" to cInternship,
                        "internshipTitle" to internshipTitle,
                        "companyId" to companyId,
                        "companyName" to companyName,
                        "studentUid" to cStudent,
                        "studentName" to sName,
                        "supervisorUid" to cSuper,
                        "supervisorName" to supName,
                        "instructorUid" to instructorUid,
                        "instructorName" to instructorName,
                        "participantUids" to participants,
                        "participantNames" to names,
                        "title" to "${internshipTitle.ifBlank { "Internship" }} Progress",
                        "subtitle" to "$sName • ${companyName.ifBlank { "Company" }} • Active Internship",
                        "status" to "ACTIVE",
                        "lastMessage" to "",
                        "lastMessageAt" to null,
                        "unreadBy" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            null
        }
            .addOnSuccessListener {
                // Period info (company-owned) appended to the student's connected notification.
                val periodSuffix = if (periodStartMs != null && periodEndMs != null) {
                    val fmt = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                    " Internship period: ${fmt.format(java.util.Date(periodStartMs!!))} to ${fmt.format(java.util.Date(periodEndMs!!))}."
                } else ""
                // Notifications are created only AFTER the critical batch commits.
                if (studentUid.isNotBlank()) {
                    val notif = firestore.collection("notifications").document()
                    notif.set(hashMapOf(
                        "notificationId" to notif.id,
                        "recipientUid" to studentUid,
                        "senderUid" to instructorUid,
                        "type" to "INTERNSHIP_CONNECTED",
                        "title" to "Internship Connected",
                        "message" to "Your instructor $instructorName has connected you with your company. Your internship is now active.$periodSuffix",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                if (supervisorUid.isNotBlank()) {
                    val notif = firestore.collection("notifications").document()
                    notif.set(hashMapOf(
                        "notificationId" to notif.id,
                        "recipientUid" to supervisorUid,
                        "senderUid" to instructorUid,
                        "type" to "INTERNSHIP_CONNECTED",
                        "title" to "Internship Connected",
                        "message" to "$studentName's internship is now active. Instructor $instructorName has connected the student with your company.",
                        "relatedId" to connectionId,
                        "relatedCompanyId" to "",
                        "relatedInternshipId" to "",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("InstructorConnect", "connectInternship transaction failed: ${e.message}")
                val raw = e.message ?: ""
                // Surface our own validation messages, but hide raw Firebase noise.
                val friendly = if (raw.contains("PERMISSION", true) ||
                    raw.contains("UNAVAILABLE", true) ||
                    raw.contains("INTERNAL", true) ||
                    raw.isBlank()
                ) "Could not connect the internship. Please try again." else raw
                onFailure(friendly)
            }
    }

    override fun setInternshipPeriod(
        connectionId: String,
        startDateMs: Long,
        endDateMs: Long,
        requiredReportsCount: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val supervisorUid = auth.currentUser?.uid ?: ""
        val connRef = firestore.collection("internshipConnections").document(connectionId)
        connRef.get().addOnSuccessListener { connDoc ->
            connRef.set(
                mapOf(
                    "startDate" to com.google.firebase.Timestamp(java.util.Date(startDateMs)),
                    "endDate" to com.google.firebase.Timestamp(java.util.Date(endDateMs)),
                    "reportFrequency" to "WEEKLY",
                    "requiredReportsCount" to requiredReportsCount,
                    "periodSetByUid" to supervisorUid,
                    "periodSetByRole" to "COMPANY_SUPERVISOR",
                    "periodSetAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
                .addOnSuccessListener {
                    // Notify related users of the (newly set) period for this old internship.
                    sendPeriodSetNotifications(
                        connectionId = connectionId,
                        studentUid = connDoc.getString("studentUid") ?: "",
                        studentName = connDoc.getString("studentName") ?: "",
                        supervisorUid = connDoc.getString("supervisorUid") ?: supervisorUid,
                        instructorUid = connDoc.getString("instructorUid") ?: "",
                        companyId = connDoc.getString("companyId") ?: "",
                        internshipId = connDoc.getString("internshipId") ?: "",
                        startDateMs = startDateMs,
                        endDateMs = endDateMs
                    )
                    onSuccess()
                }
                .addOnFailureListener { e -> onFailure(e.message ?: "Could not set internship period.") }
        }.addOnFailureListener { e -> onFailure(e.message ?: "Could not set internship period.") }
    }

    override fun submitCompanyFinalEvaluation(
        connectionId: String,
        evaluationText: String,
        rating: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = submitFinalEvaluation(connectionId, isCompany = true, evaluationText, rating, onSuccess, onFailure)

    override fun submitInstructorFinalEvaluation(
        connectionId: String,
        evaluationText: String,
        rating: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = submitFinalEvaluation(connectionId, isCompany = false, evaluationText, rating, onSuccess, onFailure)

    /**
     * Writes one side's final evaluation, then — if BOTH the company and instructor evaluations now
     * exist and the internship isn't already COMPLETED — flips status to COMPLETED and notifies all
     * three related users exactly once. The completion write is guarded by a fresh read of the status
     * to avoid duplicate completion notifications.
     */
    private fun submitFinalEvaluation(
        connectionId: String,
        isCompany: Boolean,
        evaluationText: String,
        rating: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: ""
        val connRef = firestore.collection("internshipConnections").document(connectionId)
        val evalFields = if (isCompany) mapOf(
            "companyFinalEvaluationText" to evaluationText,
            "companyFinalRating" to rating,
            "companyFinalSubmittedAt" to FieldValue.serverTimestamp(),
            "companyFinalSubmittedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        ) else mapOf(
            "instructorFinalEvaluationText" to evaluationText,
            "instructorFinalRating" to rating,
            "instructorFinalSubmittedAt" to FieldValue.serverTimestamp(),
            "instructorFinalSubmittedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        connRef.set(evalFields, SetOptions.merge())
            .addOnSuccessListener {
                connRef.get().addOnSuccessListener { doc ->
                    val conn = if (doc.exists()) docToConnection(doc) else null
                    if (conn != null && conn.hasBothFinalEvaluations() && conn.status != "COMPLETED") {
                        connRef.set(mapOf(
                            "status" to "COMPLETED",
                            "completedAt" to FieldValue.serverTimestamp(),
                            "completedByUid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ), SetOptions.merge())
                            .addOnSuccessListener { sendCompletionNotifications(conn); onSuccess() }
                            .addOnFailureListener { onSuccess() } // eval saved; completion write can be retried
                    } else onSuccess()
                }.addOnFailureListener { onSuccess() }
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not submit final evaluation.") }
    }

    /** INTERNSHIP_COMPLETED to student + instructor + company supervisor. Related users only. */
    private fun sendCompletionNotifications(conn: FirestoreInternshipConnection) {
        val name = conn.studentName.ifBlank { "The student" }
        val company = conn.companyName.ifBlank { "the company" }
        val senderUid = auth.currentUser?.uid ?: ""

        fun send(recipientUid: String, body: String) {
            if (recipientUid.isBlank()) return
            val ref = firestore.collection("notifications").document()
            ref.set(hashMapOf(
                "notificationId" to ref.id,
                "recipientUid" to recipientUid,
                "senderUid" to senderUid,
                "type" to "INTERNSHIP_COMPLETED",
                "title" to "Internship Completed",
                "message" to body,
                "relatedId" to conn.connectionId,
                "relatedCompanyId" to conn.companyId,
                "relatedInternshipId" to conn.internshipId,
                "studentUid" to conn.studentUid,
                "instructorUid" to conn.instructorUid,
                "companySupervisorUid" to conn.supervisorUid,
                "connectionId" to conn.connectionId,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }

        send(conn.studentUid, "Your internship with $company has been marked as completed.")
        send(conn.instructorUid, "$name's internship has been completed.")
        send(conn.supervisorUid, "You completed the internship for $name.")
    }

    override fun getCompletedInternshipCount(
        studentUid: String,
        scopeRole: String,
        scopeUid: String,
        scopeCompanyId: String,
        onResult: (Int) -> Unit
    ) {
        val col = firestore.collection("internshipConnections")
        // Each role queries by a field it is permitted to read, then filters to the student in code.
        val query = when (scopeRole.uppercase()) {
            "COMPANY" -> col.whereEqualTo("companyId", scopeCompanyId).whereEqualTo("status", "COMPLETED")
            "INSTRUCTOR" -> col.whereEqualTo("instructorUid", scopeUid).whereEqualTo("status", "COMPLETED")
            else -> col.whereEqualTo("studentUid", studentUid).whereEqualTo("status", "COMPLETED")
        }
        query.get()
            .addOnSuccessListener { r ->
                onResult(r.documents.count { (it.getString("studentUid") ?: "") == studentUid })
            }
            .addOnFailureListener { onResult(0) }
    }

    override fun getStudentCompletedConnections(
        studentUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Single-field equality only (no composite index); sorted client-side by completion time.
        firestore.collection("internshipConnections")
            .whereEqualTo("studentUid", studentUid)
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { r ->
                val list = r.documents.mapNotNull { docToConnection(it) }
                    .sortedByDescending { it.completedAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load completed internships.") }
    }

    // Repairs a connection that is marked ACTIVE but is missing the fields that make an
    // active internship valid. Downgrades it to WAITING_INSTRUCTOR_CONNECTION so the assigned
    // instructor must re-confirm. No-op (onRepaired(false)) for valid or non-ACTIVE docs, so it
    // never rewrites healthy documents. Only the company supervisor / admin can write per rules.
    override fun repairInvalidActiveConnection(
        connectionId: String,
        onRepaired: (Boolean) -> Unit
    ) {
        val ref = firestore.collection("internshipConnections").document(connectionId)
        ref.get()
            .addOnSuccessListener { doc ->
                val conn = if (doc.exists()) docToConnection(doc) else null
                if (conn == null || conn.status != "ACTIVE") { onRepaired(false); return@addOnSuccessListener }
                val valid = conn.instructorUid.isNotBlank() &&
                    conn.connectedAt != null &&
                    conn.connectedByInstructorUid.isNotBlank() &&
                    conn.studentUid.isNotBlank() &&
                    conn.supervisorUid.isNotBlank()
                if (valid) { onRepaired(false); return@addOnSuccessListener }

                fun writeRepair(instrUid: String, instrName: String) {
                    val updates = hashMapOf<String, Any?>(
                        "status" to "WAITING_INSTRUCTOR_CONNECTION",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    // Recover the assigned instructor from the application if the connection lost it.
                    if (conn.instructorUid.isBlank() && instrUid.isNotBlank()) {
                        updates["instructorUid"] = instrUid
                        updates["instructorName"] = instrName
                    }
                    ref.set(updates, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.w("ConnRepair", "Repaired invalid ACTIVE connection -> WAITING: $connectionId")
                            onRepaired(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e("ConnRepair", "Repair failed for $connectionId: ${e.message}")
                            onRepaired(false)
                        }
                }

                if (conn.instructorUid.isBlank() && conn.applicationId.isNotBlank()) {
                    firestore.collection("internshipApplications").document(conn.applicationId).get()
                        .addOnSuccessListener { appDoc ->
                            writeRepair(
                                appDoc.getString("assignedInstructorUid") ?: "",
                                appDoc.getString("assignedInstructorName") ?: ""
                            )
                        }
                        .addOnFailureListener { writeRepair("", "") }
                } else {
                    writeRepair(conn.instructorUid, conn.instructorName)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ConnRepair", "Could not load connection $connectionId for repair: ${e.message}")
                onRepaired(false)
            }
    }

    override fun getInstructorPendingConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Connections assigned to this instructor and awaiting connection.
        // The connection's instructorUid is set at acceptance time (carried from the
        // student's application), so a direct query finds every pending connection the
        // instructor is allowed to activate — and every returned doc passes the rules.
        firestore.collection("internshipConnections")
            .whereEqualTo("instructorUid", instructorUid)
            .whereEqualTo("status", "WAITING_INSTRUCTOR_CONNECTION")
            .get()
            .addOnSuccessListener { result ->
                val conns = result.documents.mapNotNull { docToConnection(it) }
                    .distinctBy { it.connectionId }
                onSuccess(conns)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load pending connections.") }
    }

    override fun getInstructorActiveConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections")
            .whereEqualTo("instructorUid", instructorUid)
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.documents.mapNotNull { docToConnection(it) }.distinctBy { it.connectionId })
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load internships.") }
    }

    override fun getInstructorEndedConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Single-field query per status (no composite index), merged client-side. Covers the current
        // "ENDED_BY_COMPANY" status and legacy "ENDED" docs.
        firestore.collection("internshipConnections")
            .whereEqualTo("instructorUid", instructorUid)
            .whereIn("status", listOf("ENDED_BY_COMPANY", "ENDED"))
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.documents.mapNotNull { docToConnection(it) }.distinctBy { it.connectionId })
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load internships.") }
    }

    override fun getInstructorCompletedConnections(
        instructorUid: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections")
            .whereEqualTo("instructorUid", instructorUid)
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { result ->
                val conns = result.documents.mapNotNull { docToConnection(it) }
                    .distinctBy { it.connectionId }
                    .sortedByDescending { it.completedAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                onSuccess(conns)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load internships.") }
    }

    override fun getCompanyWaitingConnections(
        companyId: String,
        onSuccess: (List<FirestoreInternshipConnection>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("internshipConnections")
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("status", "WAITING_INSTRUCTOR_CONNECTION")
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.documents.mapNotNull { docToConnection(it) })
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Could not load waiting connections.") }
    }

    override fun linkInstructorToWaitingConnection(
        connectionId: String,
        instructorUid: String,
        instructorName: String,
        onResult: (changed: Boolean) -> Unit
    ) {
        if (instructorUid.isBlank()) { onResult(false); return }
        val ref = firestore.collection("internshipConnections").document(connectionId)
        ref.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onResult(false); return@addOnSuccessListener }
                val status = doc.getString("status") ?: ""
                val existing = doc.getString("instructorUid") ?: ""
                // Only act on a connection that is still waiting and not already linked to
                // this instructor — keeps it idempotent so opening the screen won't spam.
                if (status != "WAITING_INSTRUCTOR_CONNECTION" || existing == instructorUid) {
                    onResult(false); return@addOnSuccessListener
                }
                ref.set(
                    mapOf(
                        "instructorUid" to instructorUid,
                        "instructorName" to instructorName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                    .addOnSuccessListener {
                        notifyInstructorConnectionRequest(
                            instructorUid = instructorUid,
                            connectionId = connectionId,
                            studentName = doc.getString("studentName") ?: "",
                            companyName = doc.getString("companyName") ?: "",
                            internshipTitle = doc.getString("internshipTitle") ?: "",
                            companyId = doc.getString("companyId") ?: "",
                            internshipId = doc.getString("internshipId") ?: ""
                        )
                        onResult(true)
                    }
                    .addOnFailureListener { onResult(false) }
            }
            .addOnFailureListener { onResult(false) }
    }

    override fun requestInstructorConfirmation(
        connectionId: String,
        onResult: (InstructorConfirmationOutcome) -> Unit
    ) {
        val ref = firestore.collection("internshipConnections").document(connectionId)
        ref.get()
            .addOnSuccessListener { doc ->
                val conn = if (doc.exists()) docToConnection(doc) else null
                when {
                    conn == null -> onResult(InstructorConfirmationOutcome.NOT_FOUND)
                    conn.isValidActive() -> onResult(InstructorConfirmationOutcome.ALREADY_ACTIVE)
                    conn.status != "WAITING_INSTRUCTOR_CONNECTION" ->
                        onResult(InstructorConfirmationOutcome.NOT_FOUND)
                    conn.instructorUid.isBlank() ->
                        onResult(InstructorConfirmationOutcome.NO_INSTRUCTOR)
                    else -> {
                        notifyInstructorConnectionRequest(
                            instructorUid = conn.instructorUid,
                            connectionId = connectionId,
                            studentName = conn.studentName,
                            companyName = conn.companyName,
                            internshipTitle = conn.internshipTitle,
                            companyId = conn.companyId,
                            internshipId = conn.internshipId,
                            onComplete = { ok ->
                                onResult(
                                    if (ok) InstructorConfirmationOutcome.REQUEST_SENT
                                    else InstructorConfirmationOutcome.ERROR
                                )
                            }
                        )
                    }
                }
            }
            .addOnFailureListener { onResult(InstructorConfirmationOutcome.ERROR) }
    }

    override fun addInstructorToInternship(
        applicationId: String,
        instructorUid: String,
        instructorName: String,
        onResult: (AddInstructorOutcome) -> Unit
    ) {
        Log.d("AddInstructor", "start: applicationId=$applicationId instructorUid=$instructorUid")
        if (instructorUid.isBlank()) {
            Log.e("AddInstructor", "instructorUid is blank")
            onResult(AddInstructorOutcome.ERROR); return
        }
        val connRef = firestore.collection("internshipConnections").document(applicationId)
        connRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val conn = docToConnection(doc)
                    Log.d("AddInstructor", "connection exists: status=${conn?.status} instructorUid=${conn?.instructorUid}")
                    when {
                        conn == null -> onResult(AddInstructorOutcome.ERROR)
                        conn.status == "ACTIVE" -> onResult(AddInstructorOutcome.ALREADY_ACTIVE)
                        conn.status == "WAITING_INSTRUCTOR_CONNECTION" && conn.instructorUid == instructorUid ->
                            onResult(AddInstructorOutcome.ALREADY_WAITING)
                        conn.status == "WAITING_INSTRUCTOR_CONNECTION" || conn.status == "REJECTED" ->
                            patchInstructorOntoConnection(connRef, conn, instructorUid, instructorName, onResult)
                        else -> onResult(AddInstructorOutcome.ERROR) // ENDED etc.
                    }
                } else {
                    // Old data: no connection exists yet — build one from the application.
                    Log.d("AddInstructor", "no connection doc — creating from application")
                    createConnectionFromApplication(applicationId, instructorUid, instructorName, onResult)
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddInstructor", "read connection failed", e)
                onResult(mapWriteError(e))
            }
    }

    private fun patchInstructorOntoConnection(
        connRef: com.google.firebase.firestore.DocumentReference,
        conn: FirestoreInternshipConnection,
        instructorUid: String,
        instructorName: String,
        onResult: (AddInstructorOutcome) -> Unit
    ) {
        val fields = mapOf(
            "instructorUid" to instructorUid,
            "instructorName" to instructorName,
            "status" to "WAITING_INSTRUCTOR_CONNECTION",
            "updatedAt" to "<serverTimestamp>"
        )
        Log.d("AddInstructor", "WRITE op=UPDATE(merge) collection=internshipConnections doc=${connRef.id} uid=${auth.currentUser?.uid}")
        Log.d("AddInstructor", "  existing: studentUid=${conn.studentUid} status=${conn.status} companyId=${conn.companyId} supervisorUid=${conn.supervisorUid} instructorUid=${conn.instructorUid}")
        Log.d("AddInstructor", "  fields=$fields  uidMatch(studentUid==auth)=${conn.studentUid == auth.currentUser?.uid}")
        connRef.set(
            mapOf(
                "instructorUid" to instructorUid,
                "instructorName" to instructorName,
                "status" to "WAITING_INSTRUCTOR_CONNECTION",
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
            .addOnSuccessListener {
                Log.d("AddInstructor", "patched instructor onto connection ${conn.connectionId}; status=WAITING_INSTRUCTOR_CONNECTION")
                notifyInstructorConnectionRequest(
                    instructorUid = instructorUid,
                    connectionId = conn.connectionId,
                    studentName = conn.studentName,
                    companyName = conn.companyName,
                    internshipTitle = conn.internshipTitle,
                    companyId = conn.companyId,
                    internshipId = conn.internshipId,
                    onComplete = { ok -> Log.d("AddInstructor", "instructor notification created=$ok") }
                )
                onResult(AddInstructorOutcome.LINKED)
            }
            .addOnFailureListener { e ->
                Log.e("AddInstructor", "patch connection failed", e)
                onResult(mapWriteError(e))
            }
    }

    private fun createConnectionFromApplication(
        applicationId: String,
        instructorUid: String,
        instructorName: String,
        onResult: (AddInstructorOutcome) -> Unit
    ) {
        val connRef = firestore.collection("internshipConnections").document(applicationId)
        firestore.collection("internshipApplications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->
                val app = if (appDoc.exists()) docToApplication(appDoc) else null
                if (app == null) {
                    Log.e("AddInstructor", "no application doc for $applicationId")
                    onResult(AddInstructorOutcome.NO_APPLICATION); return@addOnSuccessListener
                }
                if (app.companyId.isBlank() && app.supervisorUid.isBlank()) {
                    Log.e("AddInstructor", "application has no company/supervisor")
                    onResult(AddInstructorOutcome.NO_COMPANY); return@addOnSuccessListener
                }

                fun writeConn(supervisorUid: String, supervisorName: String) {
                    val data = hashMapOf(
                        "connectionId" to applicationId,
                        "applicationId" to applicationId,
                        "studentUid" to app.studentUid,
                        "studentName" to app.studentName,
                        "companyId" to app.companyId,
                        "companyName" to app.companyName,
                        "supervisorUid" to supervisorUid,
                        "supervisorName" to supervisorName,
                        "internshipId" to app.offerId,
                        "internshipTitle" to app.offerTitle,
                        "instructorUid" to instructorUid,
                        "instructorName" to instructorName,
                        "status" to "WAITING_INSTRUCTOR_CONNECTION",
                        "connectedAt" to null,
                        "connectedByInstructorUid" to "",
                        "startedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    Log.d("AddInstructor", "WRITE op=CREATE collection=internshipConnections doc=$applicationId uid=${auth.currentUser?.uid}")
                    Log.d("AddInstructor", "  fields: studentUid=${app.studentUid} status=WAITING_INSTRUCTOR_CONNECTION companyId=${app.companyId} supervisorUid=$supervisorUid instructorUid=$instructorUid")
                    Log.d("AddInstructor", "  uidMatch(studentUid==auth)=${app.studentUid == auth.currentUser?.uid}")
                    connRef.set(data)
                        .addOnSuccessListener {
                            Log.d("AddInstructor", "created connection $applicationId; status=WAITING_INSTRUCTOR_CONNECTION instructorUid=$instructorUid")
                            notifyInstructorConnectionRequest(
                                instructorUid = instructorUid,
                                connectionId = applicationId,
                                studentName = app.studentName,
                                companyName = app.companyName,
                                internshipTitle = app.offerTitle,
                                companyId = app.companyId,
                                internshipId = app.offerId,
                                onComplete = { ok -> Log.d("AddInstructor", "instructor notification created=$ok") }
                            )
                            onResult(AddInstructorOutcome.LINKED)
                        }
                        .addOnFailureListener { e ->
                            Log.e("AddInstructor", "create connection failed", e)
                            onResult(mapWriteError(e))
                        }
                }

                // Resolve supervisor uid/name (application carries supervisorUid; fall back to the
                // company doc). Student may read COMPANY_SUPERVISOR profiles and company docs.
                when {
                    app.supervisorUid.isNotBlank() ->
                        firestore.collection("users").document(app.supervisorUid).get()
                            .addOnSuccessListener { sdoc ->
                                writeConn(app.supervisorUid, sdoc.getString("fullName") ?: app.companyName)
                            }
                            .addOnFailureListener { writeConn(app.supervisorUid, app.companyName) }
                    app.companyId.isNotBlank() ->
                        firestore.collection("companies").document(app.companyId).get()
                            .addOnSuccessListener { cdoc ->
                                writeConn(cdoc.getString("supervisorUid") ?: "", cdoc.getString("name") ?: app.companyName)
                            }
                            .addOnFailureListener { writeConn("", app.companyName) }
                    else -> writeConn("", app.companyName)
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddInstructor", "read application failed", e)
                onResult(mapWriteError(e))
            }
    }

    /** Maps a Firestore exception to a permission-aware outcome (so the UI can be specific). */
    private fun mapWriteError(e: Exception): AddInstructorOutcome {
        val msg = e.message ?: ""
        return if (msg.contains("PERMISSION", ignoreCase = true) || msg.contains("PERMISSION_DENIED", ignoreCase = true))
            AddInstructorOutcome.PERMISSION_DENIED
        else AddInstructorOutcome.ERROR
    }

    /** Creates the "new internship connection request" notification for an instructor. */
    private fun notifyInstructorConnectionRequest(
        instructorUid: String,
        connectionId: String,
        studentName: String,
        companyName: String,
        internshipTitle: String,
        companyId: String,
        internshipId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (instructorUid.isBlank()) { onComplete?.invoke(false); return }
        val student = studentName.ifBlank { "A student" }
        val company = companyName.ifBlank { "a company" }
        val title = internshipTitle.ifBlank { "an internship" }
        val notif = firestore.collection("notifications").document()
        notif.set(
            hashMapOf(
                "notificationId" to notif.id,
                "recipientUid" to instructorUid,
                "senderUid" to (auth.currentUser?.uid ?: ""),
                "type" to "INTERNSHIP_NEEDS_INSTRUCTOR_CONNECTION",
                "title" to "New Internship Connection Request",
                "message" to "$student and $company requested your confirmation for $title.",
                "relatedId" to connectionId,
                "relatedCompanyId" to companyId,
                "relatedInternshipId" to internshipId,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    private fun docToCompany(doc: DocumentSnapshot): FirestoreCompany? {
        return try {
            FirestoreCompany(
                companyId = doc.getString("companyId") ?: doc.id,
                supervisorUid = doc.getString("supervisorUid") ?: "",
                name = doc.getString("name") ?: "",
                industry = doc.getString("industry") ?: "",
                city = doc.getString("city") ?: "",
                description = doc.getString("description") ?: "",
                size = doc.getString("size") ?: "",
                website = doc.getString("website") ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun docToConnection(doc: DocumentSnapshot): FirestoreInternshipConnection? {
        return try {
            FirestoreInternshipConnection(
                connectionId = doc.getString("connectionId") ?: doc.id,
                applicationId = doc.getString("applicationId") ?: "",
                studentUid = doc.getString("studentUid") ?: "",
                studentName = doc.getString("studentName") ?: "",
                companyId = doc.getString("companyId") ?: "",
                companyName = doc.getString("companyName") ?: "",
                supervisorUid = doc.getString("supervisorUid") ?: "",
                supervisorName = doc.getString("supervisorName") ?: "",
                internshipId = doc.getString("internshipId") ?: "",
                internshipTitle = doc.getString("internshipTitle") ?: "",
                instructorUid = doc.getString("instructorUid") ?: "",
                instructorName = doc.getString("instructorName") ?: "",
                status = doc.getString("status") ?: "WAITING_INSTRUCTOR_CONNECTION",
                startedAt = doc.getTimestamp("startedAt"),
                connectedAt = doc.getTimestamp("connectedAt"),
                connectedByInstructorUid = doc.getString("connectedByInstructorUid") ?: "",
                updatedAt = doc.getTimestamp("updatedAt"),
                startDate = doc.getTimestamp("startDate"),
                endDate = doc.getTimestamp("endDate"),
                reportFrequency = doc.getString("reportFrequency") ?: "WEEKLY",
                requiredReportsCount = (doc.getLong("requiredReportsCount") ?: 0L).toInt(),
                submittedReportsCount = (doc.getLong("submittedReportsCount") ?: 0L).toInt(),
                periodSetByUid = doc.getString("periodSetByUid") ?: "",
                periodSetByRole = doc.getString("periodSetByRole") ?: "",
                periodSetAt = doc.getTimestamp("periodSetAt"),
                companyFinalEvaluationText = doc.getString("companyFinalEvaluationText") ?: "",
                companyFinalRating = (doc.getLong("companyFinalRating") ?: 0L).toInt(),
                companyFinalSubmittedAt = doc.getTimestamp("companyFinalSubmittedAt"),
                companyFinalSubmittedByUid = doc.getString("companyFinalSubmittedByUid") ?: "",
                instructorFinalEvaluationText = doc.getString("instructorFinalEvaluationText") ?: "",
                instructorFinalRating = (doc.getLong("instructorFinalRating") ?: 0L).toInt(),
                instructorFinalSubmittedAt = doc.getTimestamp("instructorFinalSubmittedAt"),
                instructorFinalSubmittedByUid = doc.getString("instructorFinalSubmittedByUid") ?: "",
                completedAt = doc.getTimestamp("completedAt"),
                completedByUid = doc.getString("completedByUid") ?: "",
                endedBy = doc.getString("endedBy") ?: "",
                endReason = doc.getString("endReason") ?: "",
                endedByCompanyId = doc.getString("endedByCompanyId") ?: "",
                endedByCompanyName = doc.getString("endedByCompanyName") ?: "",
                endedAt = doc.getTimestamp("endedAt")
            )
        } catch (_: Exception) { null }
    }

    private fun mapAuthError(e: Exception): String {
        val msg = e.message ?: ""
        val code = if (e is FirebaseAuthException) e.errorCode else ""
        return when {
            code == "ERROR_INVALID_EMAIL" ||
            msg.contains("INVALID_EMAIL", ignoreCase = true) ->
                "Invalid email address."

            code == "ERROR_WRONG_PASSWORD" ||
            code == "ERROR_INVALID_CREDENTIAL" ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            msg.contains("INVALID_PASSWORD", ignoreCase = true) ->
                "Incorrect email or password."

            code == "ERROR_USER_NOT_FOUND" ||
            msg.contains("USER_NOT_FOUND", ignoreCase = true) ->
                "No account found with this email."

            code == "ERROR_EMAIL_ALREADY_IN_USE" ||
            msg.contains("EMAIL_EXISTS", ignoreCase = true) ||
            msg.contains("email address is already in use", ignoreCase = true) ->
                "An account already exists with this email address."

            code == "ERROR_WEAK_PASSWORD" ||
            msg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                "Password must be at least 6 characters."

            code == "ERROR_USER_DISABLED" ||
            msg.contains("USER_DISABLED", ignoreCase = true) ->
                "This account has been disabled. Please contact support."

            code == "ERROR_TOO_MANY_REQUESTS" ||
            msg.contains("TOO_MANY_ATTEMPTS_TRY_LATER", ignoreCase = true) ->
                "Too many attempts. Please wait a moment and try again."

            msg.contains("network", ignoreCase = true) ||
            msg.contains("NETWORK_ERROR", ignoreCase = true) ->
                "No internet connection. Please check your network and try again."

            else -> "An error occurred. Please try again."
        }
    }
}
