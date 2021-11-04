package net.corda.libs.permission

import net.corda.data.permissions.Group
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import java.util.*

class GroupTopicProcessor : CompactedProcessor<String, Group> {

    private val groupData: MutableMap<String, Group> = Collections.synchronizedMap(mutableMapOf())

    fun getGroup(id: String) = groupData[id]

    override val keyClass = String::class.java
    override val valueClass = Group::class.java

    override fun onSnapshot(currentData: Map<String, Group>) {
        groupData.putAll(currentData)
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