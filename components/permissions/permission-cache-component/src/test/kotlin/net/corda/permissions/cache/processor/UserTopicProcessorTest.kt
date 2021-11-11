package net.corda.permissions.cache.processor

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.User
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UserTopicProcessorTest {

    private val user = User(
        "id1", 1, ChangeDetails(Instant.now(), "changeUser"), "full name", true,
        "hashedPassword", "saltValue", false, null, null, null
    )

    private val userUpdated = User(
        "id2", 1, ChangeDetails(Instant.now(), "changeUser"), "full name", false,
        "hashedPassword", "saltValue", false, null, null, null
    )

    private val coordinator: LifecycleCoordinator = mock()
    private val userData: ConcurrentHashMap<String, User> = ConcurrentHashMap()

    @Test
    fun `New processor does not have user data`() {
        UserTopicProcessor(coordinator, userData)
        assertTrue(userData.isEmpty())
    }

    @Test
    fun `onSnapshot will add to or update the user data`() {
        val userTopicProcessor = UserTopicProcessor(coordinator, userData)
        with(userTopicProcessor) {
            onSnapshot(mapOf("user1" to User()))
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onSnapshot(mapOf("user2" to user))
            assertTrue(userData.size == 2)
            onSnapshot(mapOf("user2" to userUpdated))
            assertTrue(userData.size == 2)
        }
    }

    @Test
    fun `onNext will add to or update the user data`() {
        val userTopicProcessor = UserTopicProcessor(coordinator, userData)
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
        val userTopicProcessor = UserTopicProcessor(coordinator, userData)
        with(userTopicProcessor) {
            onNext(Record("topic", "user1", User()), null, emptyMap())
            assertTrue(userData.size == 1)
            userData.containsKey("user1")
            onNext(Record("topic", "user1", null), null, emptyMap())
            assertTrue(userData.isEmpty())
        }
    }
}