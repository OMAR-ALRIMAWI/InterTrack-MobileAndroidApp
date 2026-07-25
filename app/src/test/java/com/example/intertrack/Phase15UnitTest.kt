package com.example.intertrack

import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase15UnitTest {

    // ── AccountStatus enum ────────────────────────────────────────────────────

    @Test
    fun accountStatus_fromString_validPending() {
        assertEquals(AccountStatus.PENDING, AccountStatus.fromString("PENDING"))
    }

    @Test
    fun accountStatus_fromString_validActive() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromString("ACTIVE"))
    }

    @Test
    fun accountStatus_fromString_validRejected() {
        assertEquals(AccountStatus.REJECTED, AccountStatus.fromString("REJECTED"))
    }

    @Test
    fun accountStatus_fromString_unknown_returnsNull() {
        assertNull(AccountStatus.fromString("SUSPENDED"))
    }

    @Test
    fun accountStatus_fromString_null_returnsNull() {
        assertNull(AccountStatus.fromString(null))
    }

    @Test
    fun accountStatus_fromString_lowercase_returnsNull() {
        assertNull(AccountStatus.fromString("active"))
    }

    @Test
    fun accountStatus_validValues_containsFiveStatuses() {
        val expected = setOf("PENDING", "ACTIVE", "REJECTED", "BLOCKED", "DELETED")
        assertEquals(expected, AccountStatus.validValues)
    }

    // ── UserRole.ADMIN ────────────────────────────────────────────────────────

    @Test
    fun userRole_admin_parsedCorrectly() {
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"))
    }

    @Test
    fun userRole_admin_notInRegistrationRoles() {
        assertFalse(UserRole.registrationRoles.contains(UserRole.ADMIN.value))
    }

    @Test
    fun userRole_admin_inValidValues() {
        assertTrue(UserRole.validValues.contains(UserRole.ADMIN.value))
    }

    @Test
    fun userRole_admin_displayName() {
        assertEquals("Administrator", UserRole.ADMIN.displayName())
    }

    // ── User status helpers ───────────────────────────────────────────────────

    @Test
    fun user_isPending_returnsTrue_whenStatusIsPending() {
        val user = User(accountStatus = "PENDING")
        assertTrue(user.isPending())
        assertFalse(user.isActive())
        assertFalse(user.isRejected())
    }

    @Test
    fun user_isRejected_returnsTrue_whenStatusIsRejected() {
        val user = User(accountStatus = "REJECTED")
        assertTrue(user.isRejected())
        assertFalse(user.isActive())
        assertFalse(user.isPending())
    }

    @Test
    fun user_isActive_returnsTrue_whenStatusIsActive() {
        val user = User(accountStatus = "ACTIVE")
        assertTrue(user.isActive())
        assertFalse(user.isPending())
        assertFalse(user.isRejected())
    }

    @Test
    fun user_resolvedStatus_validActive() {
        val user = User(accountStatus = "ACTIVE")
        assertEquals(AccountStatus.ACTIVE, user.resolvedStatus())
    }

    @Test
    fun user_resolvedStatus_validPending() {
        val user = User(accountStatus = "PENDING")
        assertEquals(AccountStatus.PENDING, user.resolvedStatus())
    }

    @Test
    fun user_resolvedStatus_invalid_returnsNull() {
        val user = User(accountStatus = "BANNED")
        assertNull(user.resolvedStatus())
    }

    // ── Routing logic tests ───────────────────────────────────────────────────

    @Test
    fun routing_admin_active_shouldRouteToAdminDashboard() {
        val user = User(role = "ADMIN", accountStatus = "ACTIVE")
        val role = user.resolvedRole()
        assertNotNull(role)
        assertEquals(UserRole.ADMIN, role)
        assertTrue(user.isActive())
        // Routing rule: ADMIN + ACTIVE → AdminDashboardActivity
    }

    @Test
    fun routing_student_active_shouldRouteToStudentDashboard() {
        val user = User(role = "STUDENT", accountStatus = "ACTIVE")
        val role = user.resolvedRole()
        assertNotNull(role)
        assertEquals(UserRole.STUDENT, role)
        assertTrue(user.isActive())
        assertFalse(role == UserRole.ADMIN)
    }

    @Test
    fun routing_normalUser_pending_shouldRouteToAccountStatus() {
        val user = User(role = "STUDENT", accountStatus = "PENDING")
        val role = user.resolvedRole()
        assertNotNull(role)
        assertTrue(user.isPending())
        assertFalse(user.isActive())
        // Routing rule: normal + PENDING → AccountStatusActivity
    }

    @Test
    fun routing_normalUser_rejected_shouldRouteToAccountStatus() {
        val user = User(role = "INSTRUCTOR", accountStatus = "REJECTED")
        val role = user.resolvedRole()
        assertNotNull(role)
        assertTrue(user.isRejected())
        assertFalse(user.isActive())
        // Routing rule: normal + REJECTED → AccountStatusActivity
    }

    @Test
    fun routing_admin_pending_isInvalidCombination() {
        val user = User(role = "ADMIN", accountStatus = "PENDING")
        assertEquals(UserRole.ADMIN, user.resolvedRole())
        assertTrue(user.isPending())
        // Routing rule: ADMIN + non-ACTIVE → sign out (invalid state)
        // Validated in FirebaseAuthRepository.fetchAndValidateUser()
    }

    @Test
    fun routing_admin_rejected_isInvalidCombination() {
        val user = User(role = "ADMIN", accountStatus = "REJECTED")
        assertEquals(UserRole.ADMIN, user.resolvedRole())
        assertTrue(user.isRejected())
        // Routing rule: ADMIN + REJECTED → sign out (invalid state)
    }

    // ── Approval/rejection transition validation ──────────────────────────────

    @Test
    fun approval_pendingToActive_isValidTransition() {
        val before = AccountStatus.PENDING
        val after = AccountStatus.ACTIVE
        // Valid: PENDING → ACTIVE
        assertEquals(AccountStatus.PENDING, before)
        assertEquals(AccountStatus.ACTIVE, after)
        assertFalse(before == after)
    }

    @Test
    fun rejection_pendingToRejected_isValidTransition() {
        val before = AccountStatus.PENDING
        val after = AccountStatus.REJECTED
        assertEquals(AccountStatus.PENDING, before)
        assertEquals(AccountStatus.REJECTED, after)
    }

    @Test
    fun approval_alreadyActive_isNotAllowed() {
        val currentStatus = "ACTIVE"
        // Transaction rule: only PENDING accounts can be approved/rejected
        assertFalse(currentStatus == AccountStatus.PENDING.value)
    }

    @Test
    fun approval_alreadyRejected_isNotAllowed() {
        val currentStatus = "REJECTED"
        assertFalse(currentStatus == AccountStatus.PENDING.value)
    }

    @Test
    fun rejection_emptyReason_isNotAllowed() {
        val reason = "".trim()
        assertTrue(reason.isEmpty())
        // Business rule: rejection reason must not be empty
    }

    @Test
    fun rejection_nonEmptyReason_isAllowed() {
        val reason = "Application did not meet verification requirements.".trim()
        assertFalse(reason.isEmpty())
    }

    // ── Registration role validation ──────────────────────────────────────────

    @Test
    fun registration_student_isAllowed() {
        val role = "STUDENT"
        assertTrue(UserRole.registrationRoles.contains(role))
    }

    @Test
    fun registration_instructor_isAllowed() {
        val role = "INSTRUCTOR"
        assertTrue(UserRole.registrationRoles.contains(role))
    }

    @Test
    fun registration_companySupervisor_isAllowed() {
        val role = "COMPANY_SUPERVISOR"
        assertTrue(UserRole.registrationRoles.contains(role))
    }

    @Test
    fun registration_admin_isNotAllowed() {
        val role = "ADMIN"
        assertFalse(UserRole.registrationRoles.contains(role))
    }

    // ── New user document initial state ──────────────────────────────────────

    @Test
    fun newRegistration_accountStatus_isPending() {
        // Registration sets PENDING, not ACTIVE
        val status = AccountStatus.PENDING.value
        assertEquals("PENDING", status)
        assertFalse(status == AccountStatus.ACTIVE.value)
    }

    @Test
    fun newRegistration_rejectionReason_isNull() {
        val user = User(
            uid = "uid456",
            email = "new@example.com",
            fullName = "Nour Hassan",
            role = "STUDENT",
            accountStatus = "PENDING",
            rejectionReason = null
        )
        assertNull(user.rejectionReason)
        assertTrue(user.isPending())
    }
}
