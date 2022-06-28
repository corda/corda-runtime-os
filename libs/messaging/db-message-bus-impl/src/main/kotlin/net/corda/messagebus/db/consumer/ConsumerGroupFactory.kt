package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EntityManagerFactoryCache
import java.util.concurrent.ConcurrentHashMap

class ConsumerGroupFactory(
    private val entityManagerFactoryCache: EntityManagerFactoryCache
) {
    private val consumerGroups: MutableMap<String?, ConsumerGroup> = ConcurrentHashMap()

    fun getGroupFor(groupId: String, config: ResolvedConsumerConfig) = consumerGroups.computeIfAbsent(groupId) {
        val emf = entityManagerFactoryCache.getEmf(
            config.jdbcUrl,
            config.jdbcUser,
            config.jdbcPass
        )
        ConsumerGroup(groupId, DBAccess(emf))
    }
}
