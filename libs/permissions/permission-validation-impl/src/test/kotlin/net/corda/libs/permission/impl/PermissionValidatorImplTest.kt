package net.corda.libs.permission.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.RoleAssociation

class PermissionValidatorImplTest {

    companion object {
        private val userProcessor = UserTopicProcessor()
        private val groupProcessor = GroupTopicProcessor()
        private val roleProcessor = RoleTopicProcessor()
        private val subsFactory: SubscriptionFactory = mock()
        private val permissionService = PermissionValidatorImpl(subsFactory, userProcessor, groupProcessor, roleProcessor)

        private const val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private const val permissionString = "flow/start/com.myapp.MyFlow"

        private const val permissionUrlRequest = "https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/${permissionString}"

        private val permission = Permission(
            "5e0a07a6-c25d-413a-be34-647a792f4f58", 1,
            ChangeDetails(Instant.now(), "changeUser"),
            virtualNode,
            permissionString,
            PermissionType.ALLOW
        )

        private val permissionDenied = Permission(
            "5e0a07a6-c25d-413a-be34-647a792f4f58", 1,
            ChangeDetails(Instant.now(), "changeUser"),
            virtualNode,
            permissionString,
            PermissionType.DENY
        )

        private val role = Role(
            "roleId1", 1,
            ChangeDetails(Instant.now(), "changeUser"), "STARTFLOW-MYFLOW", listOf(PermissionAssociation(Instant.now(), permission))
        )
        private val roleWithPermDenied = Role(
            "roleId2", 1,
            ChangeDetails(Instant.now(), "changeUser"), "STARTFLOW-MYFLOW", listOf(PermissionAssociation(Instant.now(), permissionDenied))
        )
        private val user = User("user1", 1, ChangeDetails(Instant.now(), "changeUser"), "user-login-1", "full name", true,
            "hashedPassword", "saltValue", false, null, null, listOf(RoleAssociation(Instant.now(), role.id)))
        private val userWithPermDenied =
            User("userWithPermDenied", 1, ChangeDetails(Instant.now(), "changeUser"), "user-login-2", "full name", true,
                "hashedPassword", "saltValue", false, null, null, listOf(RoleAssociation(Instant.now(), roleWithPermDenied.id)))
        private val disabledUser =
            User("disabledUser", 1, ChangeDetails(Instant.now(), "changeUser"), "user-login-3", "full name", false,
                "hashedPassword", "saltValue", false, null, null, listOf(RoleAssociation(Instant.now(), role.id)))

        @BeforeAll
        @JvmStatic
        fun setUp() {

            listOf(role, roleWithPermDenied).associateBy { it.id }.let {
                roleProcessor.onSnapshot(it)
            }

            listOf(user, disabledUser, userWithPermDenied).associateBy { it.id }.let {
                userProcessor.onSnapshot(it)
            }
        }
    }

    @Test
    fun `User with proper permission will be authorized`() {

        assertTrue(permissionService.authorizeUser("requestId", user.id, permissionUrlRequest))
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