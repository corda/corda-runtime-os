package net.corda.libs.permission.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.RoleAssociation
import net.corda.libs.permissions.cache.PermissionCache
import org.mockito.kotlin.whenever

class PermissionValidatorImplTest {

    companion object {
        private val permissionCache: PermissionCache = mock()
        private val permissionService = PermissionValidatorImpl(permissionCache)

        private const val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private const val permissionString = "flow/start/com.myapp.MyFlow"

        private const val permissionUrlRequest = "https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/${permissionString}"

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
    fun `User with proper permission will be authorized`() {

        assertTrue(permissionService.authorizeUser("requestId", user.loginName, permissionUrlRequest))
    }

    @Test
    fun `Non-existing user will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", "user2", permissionUrlRequest))
    }

    @Test
    fun `Disabled user will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", disabledUser.id, permissionUrlRequest))
    }

    @Test
    fun `User with proper permission set to DENY will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", userWithPermDenied.id, permissionUrlRequest))
    }

    // More tests are to be added which verify group related permissions
}