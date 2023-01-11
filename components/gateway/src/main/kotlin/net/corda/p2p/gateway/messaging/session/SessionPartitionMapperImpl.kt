package net.corda.p2p.gateway.messaging.session

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.SessionPartitions
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.utilities.VisibleForTesting
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class SessionPartitionMapperImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig
) : SessionPartitionMapper, LifecycleWithDominoTile {

    companion object {
        private const val CONSUMER_GROUP_ID = "session_partitions_mapper"
    }

    private val sessionPartitionsMapping = ConcurrentHashMap<String, List<Int>>()
    private val processor = SessionPartitionProcessor()
    @VisibleForTesting
    internal val future = CompletableFuture<Unit>()

    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS)
    private val sessionPartitionSubscription = {
        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS),
            processor,
            messagingConfiguration
        )
    }
    private val sessionPartitionSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        sessionPartitionSubscription,
        subscriptionConfig,
        emptySet(),
        emptySet()
    )
    private val blockingDominoTile = BlockingDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, future)

    override val dominoTile = ComplexDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory,
        dependentChildren = setOf(sessionPartitionSubscriptionTile.coordinatorName, blockingDominoTile.coordinatorName),
        managedChildren = setOf(sessionPartitionSubscriptionTile.toNamedLifecycle(), blockingDominoTile.toNamedLifecycle())
    )

    override fun getPartitions(sessionId: String): List<Int>? {
        return if (!isRunning) {
            throw IllegalStateException("getPartitions invoked, while session partition mapper is not running.")
        } else {
            sessionPartitionsMapping[sessionId]
        }
    }

    private inner class SessionPartitionProcessor :
        CompactedProcessor<String, SessionPartitions> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<SessionPartitions>
            get() = SessionPartitions::class.java

        override fun onSnapshot(currentData: Map<String, SessionPartitions>) {
            sessionPartitionsMapping.putAll(currentData.map { it.key to it.value.partitions })
            future.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, SessionPartitions>,
            oldValue: SessionPartitions?,
            currentData: Map<String, SessionPartitions>
        ) {
            if (newRecord.value == null) {
                sessionPartitionsMapping.remove(newRecord.key)
            } else {
                sessionPartitionsMapping[newRecord.key] = newRecord.value!!.partitions
            }
        }
    }
}
