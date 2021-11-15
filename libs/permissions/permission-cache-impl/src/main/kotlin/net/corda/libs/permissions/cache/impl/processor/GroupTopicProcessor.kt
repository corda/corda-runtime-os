package net.corda.libs.permissions.cache.impl.processor

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.libs.permissions.cache.events.GroupTopicSnapshotReceived
import net.corda.libs.permissions.cache.processor.PermissionCacheGroupProcessor
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.records.Record

/**
 * Responsible for updating the group cache.
 */
internal class GroupTopicProcessor(
    private val coordinator: LifecycleCoordinator,
    private val groupData: ConcurrentHashMap<String, Group>
) : PermissionCacheGroupProcessor {

    override val keyClass = String::class.java
    override val valueClass = Group::class.java

    override fun onSnapshot(currentData: Map<String, Group>) {
        groupData.putAll(currentData)
        coordinator.postEvent(GroupTopicSnapshotReceived())
    }

    override fun onNext(
        newRecord: Record<String, Group>,
        oldValue: Group?,
        currentData: Map<String, Group>
    ) {
        val group = newRecord.value
        val groupId = newRecord.key

        if (group == null) {
            groupData.remove(groupId)
        } else {
            groupData[groupId] = group
        }
    }
}