package net.corda.p2p.gateway.messaging.session

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.SessionPartitions
import net.corda.p2p.gateway.domino.DominoCoordinatorFactory
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap

class SessionPartitionMapperImpl(
    coordinatorFactory: DominoCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory
) : SessionPartitionMapper, DominoTile(coordinatorFactory) {

    companion object {
        const val CONSUMER_GROUP_ID = "session_partitions_mapper"
    }

    private val sessionPartitionsMapping = ConcurrentHashMap<String, List<Int>>()

    private var sessionPartitionSubscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS),
        SessionPartitionProcessor(sessionPartitionsMapping)
    )

    override fun prepareResources() {
        keepResources(sessionPartitionSubscription)
    }

    override fun getPartitions(sessionId: String): List<Int>? {
        if (!isRunning) {
            throw IllegalStateException("getPartitions invoked, while session partition mapper is not running.")
        } else {
            return sessionPartitionsMapping[sessionId]
        }
    }

    private class SessionPartitionProcessor(private val sessionPartitionMapping: ConcurrentHashMap<String, List<Int>>) :
        CompactedProcessor<String, SessionPartitions> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<SessionPartitions>
            get() = SessionPartitions::class.java

        override fun onSnapshot(currentData: Map<String, SessionPartitions>) {
            sessionPartitionMapping.putAll(currentData.map { it.key to it.value.partitions })
        }

        override fun onNext(
            newRecord: Record<String, SessionPartitions>,
            oldValue: SessionPartitions?,
            currentData: Map<String, SessionPartitions>
        ) {
            if (newRecord.value == null) {
                sessionPartitionMapping.remove(newRecord.key)
            } else {
                sessionPartitionMapping[newRecord.key] = newRecord.value!!.partitions
            }
        }
    }
}
