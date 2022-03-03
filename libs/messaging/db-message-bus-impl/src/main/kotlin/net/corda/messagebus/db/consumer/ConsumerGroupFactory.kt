package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.persistence.DBAccess

class ConsumerGroupFactory {
    private val consumerGroups = mutableMapOf<String, ConsumerGroup>()

    fun getGroupFor(groupId: String, dbAccess: DBAccess) = consumerGroups.computeIfAbsent(groupId) { ConsumerGroup(groupId, dbAccess) }
}
