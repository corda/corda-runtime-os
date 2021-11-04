package net.corda.libs.permission

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionServiceImplTest {

    companion object {
        private val processor = PermissionsTopicProcessor()
        private val subsFactory: SubscriptionFactory = mock {
            on { createCompactedSubscription(any(), eq(processor), any()) } doReturn mock()
        }
        private val permissionService = PermissionServiceImpl(subsFactory, processor)

        private const val virtualNode = "f39d810f-6ee6-4742-ab7c-d1fe274ab85e"
        private const val permissionString = "flow/start/com.myapp.MyFlow"

        private const val permissionUrlRequest = "https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/${permissionString}"

        private val permission = Permission("5e0a07a6-c25d-413a-be34-647a792f4f58", 1,
                ChangeDetails(Instant.now(), "changeUser"),
                virtualNode,
                permissionString,
                PermissionType.ALLOW)

        private val permissionDenied = Permission("5e0a07a6-c25d-413a-be34-647a792f4f58", 1,
                ChangeDetails(Instant.now(), "changeUser"),
                virtualNode,
                permissionString,
                PermissionType.DENY)

        private val role = Role("81b6096e-d9dd-4966-a9e5-2b77c4d3b213", 1,
            ChangeDetails(Instant.now(), "changeUser"), "STARTFLOW-MYFLOW", listOf(permission))
        private val roleWithPermDenied = Role("81b6096e-d9dd-4966-a9e5-2b77c4d3b213", 1,
            ChangeDetails(Instant.now(), "changeUser"), "STARTFLOW-MYFLOW", listOf(permissionDenied))
        private val user = User("id", 1, ChangeDetails(Instant.now(), "changeUser"), "full name", true,
            "hashedPassword", "saltValue", false, null, null, listOf(role).map { it.id })
        private val userWithPermDenied = User("id", 1, ChangeDetails(Instant.now(), "changeUser"), "full name", true,
            "hashedPassword", "saltValue", false, null, null, listOf(roleWithPermDenied).map { it.id })
        private val disabledUser = User("id", 1, ChangeDetails(Instant.now(), "changeUser"), "full name", false,
            "hashedPassword", "saltValue",false, null, null, listOf(role).map { it.id })

        @BeforeAll
        @JvmStatic
        fun setUp() {
            processor.onSnapshot(mapOf("user1" to user))
            processor.onSnapshot(mapOf("disabledUser" to disabledUser))
            processor.onSnapshot(mapOf("userWithPermDenied" to userWithPermDenied))
        }
    }


    @Test
    fun `User with proper permission will be authorized`() {

        assertTrue(permissionService.authorizeUser("requestId", "user1", permissionUrlRequest))
    }

    @Test
    fun `Non-existing user will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", "user2", permissionUrlRequest))
    }

    @Test
    fun `Disabled user will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", "disabledUser", permissionUrlRequest))
    }
    @Test
    fun `User with proper permission set to DENY will not be authorized`() {

        assertFalse(permissionService.authorizeUser("requestId", "userWithPermDenied", permissionUrlRequest))
    }
}