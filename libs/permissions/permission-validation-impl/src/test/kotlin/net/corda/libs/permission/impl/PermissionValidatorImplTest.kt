package net.corda.libs.permission.impl

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.data.permissions.summary.PermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionValidatorImplTest {

    companion object {
        private val permissionCache: PermissionCache = mock()
        private val passwordService: PasswordService = mock()
        private val permissionValidator = PermissionValidatorImpl(permissionCache, passwordService)

        private const val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private const val permissionString = "flow/start/com.myapp.MyFlow"

        private const val permissionUrlRequest = "POST:https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/${permissionString}"

        private val permission = Permission(
            "allowPermissionId", 1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.ALLOW,
            permissionString,
            "group1")

        private val permissionDenied = Permission(
            "denyPermissionId", 1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.DENY,
            permissionString,
            "group1")

        private val role = Role(
            "roleId1", 1,
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

        @BeforeAll
        @JvmStatic
        fun setUp() {
            whenever(permissionCache.getUser(user.loginName)).thenReturn(user)
            whenever(permissionCache.getUser(userWithPermDenied.loginName)).thenReturn(userWithPermDenied)
            whenever(permissionCache.getUser(disabledUser.loginName)).thenReturn(disabledUser)

            whenever(permissionCache.getRole(role.id)).thenReturn(role)
            whenever(permissionCache.getRole(roleWithPermDenied.id)).thenReturn(roleWithPermDenied)

            whenever(permissionCache.getPermission(permission.id)).thenReturn(permission)
            whenever(permissionCache.getPermission(permissionDenied.id)).thenReturn(permissionDenied)
        }
    }

    @Test
    fun `authenticate user will return false when user cannot be found in cache`() {
        whenever(permissionCache.getUser("userLoginName1")).thenReturn(null)

        val result = permissionValidator.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result)
    }

    @Test
    fun `authenticate user will return false when user does not have salt value`() {
        val saltlessUser = User().apply {
            saltValue = null
            hashedPassword = "pass"
        }
        whenever(permissionCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = permissionValidator.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result)
    }

    @Test
    fun `authenticate user will return false when user does not have hashed pass`() {
        val saltlessUser = User().apply {
            saltValue = "abcsalt"
            hashedPassword = null
        }
        whenever(permissionCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = permissionValidator.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result)
    }

    @Test
    fun `authenticate user will return false when password service returns says password not verified`() {
        val storedPasswordHashCapture = argumentCaptor<PasswordHash>()
        val requestPasswordCapture = argumentCaptor<String>()

        val user = User().apply {
            saltValue = "abcsalt"
            hashedPassword = "hashedpass"
        }

        whenever(permissionCache.getUser("userLoginName2")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(false)

        val result = permissionValidator.authenticateUser("userLoginName2", "password".toCharArray())

        assertFalse(result)

        verify(permissionCache, times(1)).getUser("userLoginName2")

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
        }

        whenever(permissionCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = permissionValidator.authenticateUser("userLoginName1", "password".toCharArray())

        assertTrue(result)

        verify(permissionCache, times(1)).getUser("userLoginName1")

        assertEquals(1, storedPasswordHashCapture.allValues.size)
        assertEquals(1, requestPasswordCapture.allValues.size)
        assertNotNull(storedPasswordHashCapture.firstValue)
        assertNotNull(requestPasswordCapture.firstValue)
        assertEquals("hashedpass", storedPasswordHashCapture.firstValue.value)
        assertEquals("abcsalt", storedPasswordHashCapture.firstValue.salt)
        assertEquals("password", requestPasswordCapture.firstValue)
    }

    @Test
    fun `User with proper permission will be authorized`() {

        val userPermissionSummary = UserPermissionSummary(
            user.loginName,
            true,
            listOf(PermissionSummary("id1", null, null, "POST:.*$permissionString",
                PermissionType.ALLOW)),
            Instant.now()
        )
        whenever(permissionCache.getPermissionSummary(user.loginName)).thenReturn(userPermissionSummary)

        assertTrue(permissionValidator.authorizeUser(user.loginName, permissionUrlRequest))
    }

    @Test
    fun `Non-existing user will not be authorized`() {

        assertFalse(permissionValidator.authorizeUser("user2", permissionUrlRequest))
    }

    @Test
    fun `User with proper permission set to DENY will not be authorized`() {

        val userPermissionSummary = UserPermissionSummary(
            userWithPermDenied.loginName,
            true,
            listOf(PermissionSummary("id2",null, null, "POST:.*$permissionString",
                PermissionType.DENY)),
            Instant.now()
        )
        whenever(permissionCache.getPermissionSummary(userWithPermDenied.loginName)).thenReturn(userPermissionSummary)

        assertFalse(permissionValidator.authorizeUser(userWithPermDenied.loginName, permissionUrlRequest))
    }

    // More tests are to be added which verify group related permissions
}