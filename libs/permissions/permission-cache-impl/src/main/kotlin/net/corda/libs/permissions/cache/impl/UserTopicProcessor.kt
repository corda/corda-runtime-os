package net.corda.libs.permissions.cache.impl

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.events.UserTopicSnapshotReceived
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

/**
 * Responsible for updating the User cache.
 */
class UserTopicProcessor(
    private val coordinator: LifecycleCoordinator,
    private val userData: ConcurrentHashMap<String, User>
) : CompactedProcessor<String, User> {

    override val keyClass = String::class.java
    override val valueClass = User::class.java

    override fun onSnapshot(currentData: Map<String, User>) {
        userData.putAll(currentData)
        coordinator.postEvent(UserTopicSnapshotReceived())
    }

    override fun onNext(
            newRecord: Record<String, User>,
            oldValue: User?,
            currentData: Map<String, User>
    ) {
        val user = newRecord.value
        val userLogin = newRecord.key

        if (user == null) {
            userData.remove(userLogin)
        } else {
            userData[userLogin] = user
        }
    }
}