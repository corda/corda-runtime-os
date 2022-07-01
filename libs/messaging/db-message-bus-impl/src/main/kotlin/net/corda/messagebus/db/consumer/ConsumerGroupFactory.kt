package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EntityManagerFactoryHolder
import java.util.concurrent.ConcurrentHashMap

class ConsumerGroupFactory(
    private val entityManagerFactoryHolder: EntityManagerFactoryHolder
) {
    private val consumerGroups: MutableMap<String?, ConsumerGroup> = ConcurrentHashMap()

    fun getGroupFor(groupId: String, config: ResolvedConsumerConfig) = consumerGroups.computeIfAbsent(groupId) {
        val emf = entityManagerFactoryHolder.getEmf(
            config.jdbcUrl,
            config.jdbcUser,
            config.jdbcPass
        )
        ConsumerGroup(groupId, DBAccess(emf))
    }
}
