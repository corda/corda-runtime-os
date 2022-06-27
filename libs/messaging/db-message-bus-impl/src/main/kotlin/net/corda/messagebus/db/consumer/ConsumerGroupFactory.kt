package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EmfCache
import java.util.concurrent.ConcurrentHashMap

class ConsumerGroupFactory(
    private val emfCache: EmfCache
) {
    private val consumerGroups: MutableMap<String?, ConsumerGroup> = ConcurrentHashMap()

    fun getGroupFor(groupId: String, config: ResolvedConsumerConfig) = consumerGroups.computeIfAbsent(groupId) {
        ConsumerGroup(groupId, DBAccess(emfCache.getEmf(config)))
    }
}
