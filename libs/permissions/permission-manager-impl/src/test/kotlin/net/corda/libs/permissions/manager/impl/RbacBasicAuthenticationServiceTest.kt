package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.ExpiryStatus
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class RbacBasicAuthenticationServiceTest {

    companion object {
        private val passwordService: PasswordService = mock()
        private val permissionManagementCache = mock<PermissionManagementCache>()
        private val rbacConfig = mock<SmartConfig>()
        private val passwordExpiryTime = Instant.now().plus(1L, ChronoUnit.DAYS)

        private val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private val permissionString = "flow/start/com.myapp.MyFlow"

        private val permission = Permission(
            "allowPermissionId",
            1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.ALLOW,
            permissionString,
            "group1"
        )

        private val permissionDenied = Permission(
            "denyPermissionId",
            1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.DENY,
            permissionString,
            "group1"
        )

        private val role = Role(
            "roleId1",
            1,
            ChangeDetails(Instant.now()),
            "STARTFLOW-MYFLOW",
            "group1",
            listOf(
                PermissionAssociation(
                    ChangeDetails(Instant.now()),
                    permission.id
                )
            )
        )
        private val roleWithPermDenied = Role(
            "roleId2",
            1,
            ChangeDetails(Instant.now()),
            "STARTFLOW-MYFLOW",
            "group1",
            listOf(PermissionAssociation(ChangeDetails(Instant.now()), permissionDenied.id))
        )
        private val user = User(
            "user1",
            1,
            ChangeDetails(Instant.now()),
            "user-login1",
            "full name",
            true,
            "hashedPassword",
            "saltValue",
            null,
            false,
            null,
            null,
            listOf(RoleAssociation(ChangeDetails(Instant.now()), role.id))
        )
        private val userWithPermDenied = User(
            "userWithPermDenied",
            1,
            ChangeDetails(Instant.now()),
            "user-login2",
            "full name",
            true,
            "hashedPassword",
            "saltValue",
            null,
            false,
            null,
            null,
            listOf(RoleAssociation(ChangeDetails(Instant.now()), roleWithPermDenied.id))
        )
        private val disabledUser = User(
            "disabledUser",
            1,
            ChangeDetails(Instant.now()),
            "user-login3",
            "full name",
            false,
            "hashedPassword",
            "saltValue",
            null,
            false,
            null,
            null,
            listOf(RoleAssociation(ChangeDetails(Instant.now()), role.id))
        )

        @JvmStatic
        @BeforeAll
        fun setUp() {
            whenever(permissionManagementCache.getUser(user.loginName)).thenReturn(user)
            whenever(permissionManagementCache.getUser(userWithPermDenied.loginName)).thenReturn(userWithPermDenied)
            whenever(permissionManagementCache.getUser(disabledUser.loginName)).thenReturn(disabledUser)
            whenever(permissionManagementCache.getRole(role.id)).thenReturn(role)
            whenever(permissionManagementCache.getRole(roleWithPermDenied.id)).thenReturn(roleWithPermDenied)
            whenever(permissionManagementCache.getPermission(permission.id)).thenReturn(permission)
            whenever(permissionManagementCache.getPermission(permissionDenied.id)).thenReturn(permissionDenied)

            whenever(rbacConfig.getInt(ConfigKeys.RBAC_PASSWORD_EXPIRY_WARNING_WINDOW)).thenReturn(30)
        }
    }

    private val rbacBasicAuthenticationService =
        RbacBasicAuthenticationService(rbacConfig, UTCClock(), AtomicReference(permissionManagementCache), passwordService)

    @Test
    fun `authenticate user will return false when user cannot be found in cache`() {
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(null)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result.authenticationSuccess)
    }

    @Test
    fun `authenticate user will return false when user does not have salt value`() {
        val saltlessUser = User().apply {
            saltValue = null
            hashedPassword = "pass"
        }
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result.authenticationSuccess)
    }

    @Test
    fun `authenticate user will return false when user does not have hashed pass`() {
        val saltlessUser = User().apply {
            saltValue = "abcsalt"
            hashedPassword = null
        }
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result.authenticationSuccess)
    }

    @Test
    fun `authenticate user will return false when password service returns says password not verified`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
        }

        whenever(permissionManagementCache.getUser("userLoginName2")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(false)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName2", "password".toCharArray())

        assertFalse(result.authenticationSuccess)

        verify(permissionManagementCache, times(1)).getUser("userLoginName2")

        assertEquals(1, storedPasswordHashCapture.allValues.size)
        assertEquals(1, requestPasswordCapture.allValues.size)
        assertNotNull(storedPasswordHashCapture.firstValue)
        assertNotNull(requestPasswordCapture.firstValue)
        assertEquals("hashedpass", storedPasswordHashCapture.firstValue.value)
        assertEquals("abcsalt", storedPasswordHashCapture.firstValue.salt)
        assertEquals("password", requestPasswordCapture.firstValue)
    }

    @Test
    fun `authenticate user return true when password service says password verified`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
            passwordExpiry = passwordExpiryTime
        }

        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertTrue(result.authenticationSuccess)

        verify(permissionManagementCache, times(1)).getUser("userLoginName1")

        assertEquals(1, storedPasswordHashCapture.allValues.size)
        assertEquals(1, requestPasswordCapture.allValues.size)
        assertNotNull(storedPasswordHashCapture.firstValue)
        assertNotNull(requestPasswordCapture.firstValue)
        assertEquals("hashedpass", storedPasswordHashCapture.firstValue.value)
        assertEquals("abcsalt", storedPasswordHashCapture.firstValue.salt)
        assertEquals("password", requestPasswordCapture.firstValue)
    }

    @Test
    fun `authenticate user will return true and expiryStatus EXPIRED when password expired`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()
        val minimumClock = mock<Clock>().apply {
            whenever(instant()).thenReturn(Instant.MIN)
        }

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
            passwordExpiry = minimumClock.instant()
        }

        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        verify(permissionManagementCache, atLeastOnce()).getUser("userLoginName1")

        assertTrue(result.authenticationSuccess)
        assertEquals(ExpiryStatus.EXPIRED, result.expiryStatus)
    }

    @Test
    fun `authenticate user will return true and expiryStatus CLOSE_TO_EXPIRY when within expiry warning window`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
            passwordExpiry = passwordExpiryTime
        }

        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        verify(permissionManagementCache, atLeastOnce()).getUser("userLoginName1")

        assertTrue(result.authenticationSuccess)
        assertEquals(ExpiryStatus.CLOSE_TO_EXPIRY, result.expiryStatus)
    }

    @Test
    fun `authenticate user will return true and expiryStatus ACTIVE when outside of expiry warning window`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()
        val passwordExpiryActive = Instant.now().plus(rbacConfig.getInt(ConfigKeys.RBAC_PASSWORD_EXPIRY_WARNING_WINDOW).toLong().plus(1), ChronoUnit.DAYS)

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
            passwordExpiry = passwordExpiryActive
        }

        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        verify(permissionManagementCache, atLeastOnce()).getUser("userLoginName1")

        assertTrue(result.authenticationSuccess)
        assertEquals(ExpiryStatus.ACTIVE, result.expiryStatus)
    }
}
