package net.corda.libs.permissions.cache.impl

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Role
import net.corda.libs.permissions.cache.events.RoleTopicSnapshotReceived
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

/**
 * Responsible for updating the Role cache.
 */
class RoleTopicProcessor(
    private val coordinator: LifecycleCoordinator,
    private val roleData: ConcurrentHashMap<String, Role>
) : CompactedProcessor<String, Role> {

    override val keyClass = String::class.java
    override val valueClass = Role::class.java

    override fun onSnapshot(currentData: Map<String, Role>) {
        roleData.putAll(currentData)
        coordinator.postEvent(RoleTopicSnapshotReceived())
    }

    override fun onNext(
            newRecord: Record<String, Role>,
            oldValue: Role?,
            currentData: Map<String, Role>
    ) {
        val role = newRecord.value
        val roleId = newRecord.key

        if (role == null) {
            roleData.remove(roleId)
        } else {
            roleData[roleId] = role
        }
    }
}