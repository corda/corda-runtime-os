package net.corda.libs.permissions.cache.impl.processor

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.User
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermissionTopicProcessorTest {

    private val user = User("id1", 1, ChangeDetails(Instant.now()), "user-login1", "full name", true,
    "hashedPassword", "saltValue", null, false, null, null, emptyList())

    private val userUpdated = User("id2", 1, ChangeDetails(Instant.now()), "user-login2", "full name", true,
        "hashedPassword", "saltValue", null, false, null, null, emptyList())

    private val userData: ConcurrentHashMap<String, User> = ConcurrentHashMap()

    @Test
    fun `New processor does not have user data`() {
        var callbackExecuted = false
        PermissionTopicProcessor(String::class.java, User::class.java, userData) { callbackExecuted = true }
        assertTrue(userData.isEmpty())
        assertFalse(callbackExecuted)
    }

    @Test
    fun `onSnapshot will add to or update the user data`() {
        var callbackExecuted = false
        val userTopicProcessor = PermissionTopicProcessor(String::class.java, User::class.java, userData) { callbackExecuted = true }
        with(userTopicProcessor) {
            onSnapshot(mapOf("user1" to User()))
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onSnapshot(mapOf("user2" to user))
            assertTrue(userData.size == 2)
            onSnapshot(mapOf("user2" to userUpdated))
            assertTrue(userData.size == 2)
            assertTrue(callbackExecuted)
        }
    }

    @Test
    fun `onNext will add to or update the user data`() {
        val userTopicProcessor = PermissionTopicProcessor(String::class.java, User::class.java, userData) {  }
        with(userTopicProcessor) {
            onNext(Record("topic", "user1", User()), null, emptyMap())
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onNext(Record("topic", "user2", user), null, emptyMap())
            assertTrue(userData.size == 2)
            onNext(Record("topic", "user2", userUpdated), null, emptyMap())
            assertTrue(userData.size == 2)
        }
    }

    @Test
    fun `onNext null Record value will delete user`() {
        val userTopicProcessor = PermissionTopicProcessor(String::class.java, User::class.java, userData) {  }
        with(userTopicProcessor) {
            onNext(Record("topic", "user1", User()), null, emptyMap())
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onNext(Record("topic", "user1", null), null, emptyMap())
            assertTrue(userData.isEmpty())
        }
    }
}