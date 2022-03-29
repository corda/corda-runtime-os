package net.corda.libs.permissions.manager.impl

import java.time.Instant
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RbacBasicAuthenticationServiceTest {

    companion object {
        private val passwordService: PasswordService = mock()
        private val permissionManagementCache: PermissionManagementCache = mock()
        private val rbacBasicAuthenticationService = RbacBasicAuthenticationService(permissionManagementCache, passwordService)

        private val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private val permissionString = "flow/start/com.myapp.MyFlow"

        private val permission = Permission(
            "allowPermissionId", 1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.ALLOW,
            permissionString,
            "group1"
        )

        private val permissionDenied = Permission(
            "denyPermissionId", 1,
            ChangeDetails(Instant.now()),
            virtualNode,
            PermissionType.DENY,
            permissionString,
            "group1"
        )

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
        }
    }

    @Test
    fun `authenticate user will return false when user cannot be found in cache`() {
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(null)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result)
    }

    @Test
    fun `authenticate user will return false when user does not have salt value`() {
        val saltlessUser = User().apply {
            saltValue = null
            hashedPassword = "pass"
        }
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertFalse(result)
    }

    @Test
    fun `authenticate user will return false when user does not have hashed pass`() {
        val saltlessUser = User().apply {
            saltValue = "abcsalt"
            hashedPassword = null
        }
        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(saltlessUser)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

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

        whenever(permissionManagementCache.getUser("userLoginName2")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(false)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName2", "password".toCharArray())

        assertFalse(result)

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
        }

        whenever(permissionManagementCache.getUser("userLoginName1")).thenReturn(user)
        whenever(passwordService.verifies(requestPasswordCapture.capture(), storedPasswordHashCapture.capture())).thenReturn(true)

        val result = rbacBasicAuthenticationService.authenticateUser("userLoginName1", "password".toCharArray())

        assertTrue(result)

        verify(permissionManagementCache, times(1)).getUser("userLoginName1")

        assertEquals(1, storedPasswordHashCapture.allValues.size)
        assertEquals(1, requestPasswordCapture.allValues.size)
        assertNotNull(storedPasswordHashCapture.firstValue)
        assertNotNull(requestPasswordCapture.firstValue)
        assertEquals("hashedpass", storedPasswordHashCapture.firstValue.value)
        assertEquals("abcsalt", storedPasswordHashCapture.firstValue.salt)
        assertEquals("password", requestPasswordCapture.firstValue)
    }
}