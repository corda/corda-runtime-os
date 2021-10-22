package net.corda.libs.permission

import net.corda.data.permissions.User

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import net.corda.messaging.api.records.Record
import kotlin.test.assertNotNull
import java.time.Instant
import kotlin.test.assertFalse

class PermissionTopicProcessorTest {

    private val user = User("id", Instant.now(), "full name", true, "email", false, null, null, null)
    private val userUpdated = User("id", Instant.now(), "full name", false, "email", false, null, null, null)

    @Test
    fun `New processor does not have user data`() {
        with (PermissionsTopicProcessor()) {
            assertTrue(userData.isEmpty())
        }
    }

    @Test
    fun `onSnapshot will add to or update the user data`() {
        with(PermissionsTopicProcessor()) {
            onSnapshot(mapOf("user1" to User()))
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onSnapshot(mapOf("user2" to user))
            assertTrue(userData.size == 2)
            val user2 = getUser("user2")
            assertNotNull(user2)
            assertTrue(user2.enabled)

            onSnapshot(mapOf("user2" to userUpdated))
            assertTrue(userData.size == 2)
            val user2Updated = getUser("user2")
            assertNotNull(user2Updated)
            assertFalse(user2Updated.enabled)
        }
    }

    @Test
    fun `onNext will add to or update the user data`() {


        with(PermissionsTopicProcessor()) {
            onNext(Record("topic", "user1", User()), null, emptyMap())
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onNext(Record("topic", "user2", user), null, emptyMap())
            assertTrue(userData.size == 2)
            val user2 = getUser("user2")
            assertNotNull(user2)
            assertTrue(user2.enabled)


            onNext(Record("topic", "user2", userUpdated), null, emptyMap())
            assertTrue(userData.size == 2)
            val user2Updated = getUser("user2")
            assertNotNull(user2Updated)
            assertFalse(user2Updated.enabled)
        }
    }

    @Test
    fun `onNext null Record value will delete user`() {
        with(PermissionsTopicProcessor()) {
            onNext(Record("topic", "user1", User()), null, emptyMap())
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onNext(Record("topic", "user1",null), null, emptyMap())
            assertTrue(userData.isEmpty())
        }
    }
}