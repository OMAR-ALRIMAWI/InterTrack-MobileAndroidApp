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

class AuthUnitTest {

    // ── UserRole.fromString ────────────────────────────────────────────────────

    @Test
    fun fromString_validStudent_returnsStudent() {
        assertEquals(UserRole.STUDENT, UserRole.fromString("STUDENT"))
    }

    @Test
    fun fromString_validInstructor_returnsInstructor() {
        assertEquals(UserRole.INSTRUCTOR, UserRole.fromString("INSTRUCTOR"))
    }

    @Test
    fun fromString_validCompanySupervisor_returnsCompanySupervisor() {
        assertEquals(UserRole.COMPANY_SUPERVISOR, UserRole.fromString("COMPANY_SUPERVISOR"))
    }

    @Test
    fun fromString_validAdmin_returnsAdmin() {
        // Phase 1.5: ADMIN is now a valid role
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"))
    }

    @Test
    fun fromString_unknownValue_returnsNull() {
        // "SUPER_ADMIN" is not a valid role — use a truly unknown value
        assertNull(UserRole.fromString("SUPER_ADMIN"))
    }

    @Test
    fun fromString_null_returnsNull() {
        assertNull(UserRole.fromString(null))
    }

    @Test
    fun fromString_lowercaseInput_returnsNull() {
        // Role matching is case-sensitive
        assertNull(UserRole.fromString("student"))
    }

    @Test
    fun fromString_emptyString_returnsNull() {
        assertNull(UserRole.fromString(""))
    }

    // ── UserRole.validValues ──────────────────────────────────────────────────

    @Test
    fun validValues_containsAllFourRoles() {
        // Phase 1.5: ADMIN added to validValues
        val expected = setOf("STUDENT", "INSTRUCTOR", "COMPANY_SUPERVISOR", "ADMIN")
        assertEquals(expected, UserRole.validValues)
    }

    @Test
    fun validValues_doesNotContainUnknownRole() {
        assertFalse(UserRole.validValues.contains("SUPER_ADMIN"))
    }

    @Test
    fun validValues_containsAdmin() {
        assertTrue(UserRole.validValues.contains("ADMIN"))
    }

    // ── UserRole.registrationRoles ────────────────────────────────────────────

    @Test
    fun registrationRoles_excludesAdmin() {
        assertFalse(UserRole.registrationRoles.contains("ADMIN"))
    }

    @Test
    fun registrationRoles_containsThreeNormalRoles() {
        val expected = setOf("STUDENT", "INSTRUCTOR", "COMPANY_SUPERVISOR")
        assertEquals(expected, UserRole.registrationRoles)
    }

    // ── User.resolvedRole ─────────────────────────────────────────────────────

    @Test
    fun resolvedRole_validRole_returnsEnum() {
        val user = User(role = "INSTRUCTOR")
        assertEquals(UserRole.INSTRUCTOR, user.resolvedRole())
    }

    @Test
    fun resolvedRole_invalidRole_returnsNull() {
        val user = User(role = "UNKNOWN")
        assertNull(user.resolvedRole())
    }

    @Test
    fun resolvedRole_emptyRole_returnsNull() {
        val user = User(role = "")
        assertNull(user.resolvedRole())
    }

    // ── User.isActive ─────────────────────────────────────────────────────────

    @Test
    fun isActive_activeStatus_returnsTrue() {
        val user = User(accountStatus = "ACTIVE")
        assertTrue(user.isActive())
    }

    @Test
    fun isActive_pendingStatus_returnsFalse() {
        val user = User(accountStatus = "PENDING")
        assertFalse(user.isActive())
    }

    @Test
    fun isActive_rejectedStatus_returnsFalse() {
        val user = User(accountStatus = "REJECTED")
        assertFalse(user.isActive())
    }

    @Test
    fun isActive_emptyStatus_returnsFalse() {
        val user = User(accountStatus = "")
        assertFalse(user.isActive())
    }

    // ── Dashboard routing logic ───────────────────────────────────────────────

    @Test
    fun userRole_value_matchesStoredString() {
        assertEquals("STUDENT", UserRole.STUDENT.value)
        assertEquals("INSTRUCTOR", UserRole.INSTRUCTOR.value)
        assertEquals("COMPANY_SUPERVISOR", UserRole.COMPANY_SUPERVISOR.value)
        assertEquals("ADMIN", UserRole.ADMIN.value)
    }

    @Test
    fun accountStatusActive_constantValue() {
        assertEquals("ACTIVE", UserRole.ACCOUNT_STATUS_ACTIVE)
    }

    @Test
    fun fullUserDocument_validActiveStudent_resolveCorrectly() {
        val user = User(
            uid = "uid123",
            email = "student@uni.edu",
            fullName = "Ali Al-Qahtani",
            role = "STUDENT",
            accountStatus = "ACTIVE"
        )
        assertNotNull(user.resolvedRole())
        assertEquals(UserRole.STUDENT, user.resolvedRole())
        assertTrue(user.isActive())
        assertFalse(user.isPending())
        assertFalse(user.isRejected())
    }
}
