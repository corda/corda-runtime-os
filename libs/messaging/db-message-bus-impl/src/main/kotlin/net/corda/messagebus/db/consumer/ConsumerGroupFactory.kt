package net.corda.messagebus.db.consumer

import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.create
import net.corda.orm.EntityManagerFactoryFactory
import java.util.concurrent.ConcurrentHashMap

class ConsumerGroupFactory(
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) {
    private val consumerGroups: MutableMap<String?, ConsumerGroup> = ConcurrentHashMap()

    fun getGroupFor(groupId: String, config: ResolvedConsumerConfig) = consumerGroups.computeIfAbsent(groupId) {
        val dbAccess = DBAccess(
            entityManagerFactoryFactory.create(
                config,
                "Consumer Group for $groupId",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                )
            )
        )
        ConsumerGroup(groupId, dbAccess)
    }
}
