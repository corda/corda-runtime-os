package net.corda.p2p.gateway.messaging.session

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.SessionPartitions
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class SessionPartitionMapperImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
) : SessionPartitionMapper, LifecycleWithDominoTile {

    companion object {
        const val CONSUMER_GROUP_ID = "session_partitions_mapper"
    }

    private val sessionPartitionsMapping = ConcurrentHashMap<String, List<Int>>()
    private val processor = SessionPartitionProcessor()
    override val dominoTile = DominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ::createResources)
    private val future = AtomicReference<CompletableFuture<Unit>>()

    private val sessionPartitionSubscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS, instanceId),
        processor,
        nodeConfiguration
    )

    override fun getPartitions(sessionId: String): List<Int>? {
        return if (!isRunning) {
            throw IllegalStateException("getPartitions invoked, while session partition mapper is not running.")
        } else {
            println("QQQ getting partition $sessionId - known? - ${sessionPartitionsMapping.contains(sessionId)}")
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
            println("QQQ got snapshots $currentData")
            sessionPartitionsMapping.putAll(currentData.map { it.key to it.value.partitions })
            future.get().complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, SessionPartitions>,
            oldValue: SessionPartitions?,
            currentData: Map<String, SessionPartitions>
        ) {
            println("QQQ got next ${newRecord.key} -> ${newRecord.value}")
            if (newRecord.value == null) {
                sessionPartitionsMapping.remove(newRecord.key)
            } else {
                sessionPartitionsMapping[newRecord.key] = newRecord.value!!.partitions
            }
            println("QQQ after got next ${newRecord.key} -> ${sessionPartitionsMapping[newRecord.key]}")
        }
    }

    fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val resourceFuture = CompletableFuture<Unit>()
        future.set(resourceFuture)
        sessionPartitionSubscription.start()
        resources.keep { sessionPartitionSubscription.stop() }
        return resourceFuture
    }
}
