package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.persistence.DBAccess
import java.util.concurrent.ConcurrentHashMap

class ConsumerGroupFactory {
    private val consumerGroups: MutableMap<String?, ConsumerGroup> = ConcurrentHashMap()

    fun getGroupFor(groupId: String, dbAccess: DBAccess) = consumerGroups.computeIfAbsent(groupId) {
        ConsumerGroup(groupId, dbAccess)
    }
}
