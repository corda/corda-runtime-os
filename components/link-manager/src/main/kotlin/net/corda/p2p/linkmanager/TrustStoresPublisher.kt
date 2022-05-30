package net.corda.p2p.linkmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.v5.base.util.contextLogger

@Suppress("LongParameterList")
internal class TrustStoresPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile, GroupPolicyListener {

    companion object {
        val logger = contextLogger()
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_truststore_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_truststore_writer"
    }

    private val publishedGroups = ConcurrentHashMap<String, Set<PemCertificates>>()
    private val toPublish = ConcurrentLinkedQueue<GroupPolicyListener.GroupInfo>()
    private val ready = CompletableFuture<Unit>()
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        registry,
        PublisherConfig(MISSING_DATA_WRITER_GROUP_NAME, false),
        messagingConfiguration,
    )
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_TLS_TRUSTSTORES),
        Processor(),
        messagingConfiguration,
    )
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )
    private val blockingDominoTile = BlockingDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override fun groupAdded(groupInfo: GroupPolicyListener.GroupInfo) {
        toPublish.offer(groupInfo)
        if (dominoTile.isRunning) publishQueue()
    }

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        registry,
        onStart = ::onStart,
        managedChildren = listOf(
            publisher.dominoTile,
            subscriptionTile,
            blockingDominoTile
        ),
        dependentChildren = listOf(
            publisher.dominoTile.coordinatorName,
            subscriptionTile.coordinatorName,
            blockingDominoTile.coordinatorName
        )
    )

    private inner class Processor : CompactedProcessor<String, GatewayTruststore> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java
        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            publishedGroups.putAll(
                currentData.mapValues {
                    it.value.trustedCertificates.toSet()
                }
            )
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTruststore>,
            oldValue: GatewayTruststore?,
            currentData: Map<String, GatewayTruststore>,
        ) {
            val certificates = newRecord.value?.trustedCertificates
            if (certificates == null) {
                publishedGroups.remove(newRecord.key)
            } else {
                publishedGroups[newRecord.key] = certificates.toSet()
            }
        }
    }

    private fun onStart(): CompletableFuture<Unit> {
        publishQueue()
        return CompletableFuture.completedFuture(Unit)
    }

    private fun publishQueue() {
        while (true) {
            val groupInfo = toPublish.poll() ?: return
            val groupId = groupInfo.groupId
            val certificates = groupInfo.trustedCertificates
            publishGroupIfNeeded(groupId, certificates)
        }
    }

    private fun publishGroupIfNeeded(groupId: String, certificates: List<PemCertificates>) {
        publishedGroups.compute(groupId) { _, publishedCertificates ->
            val certificatesSet = certificates.toSet()
            if (certificatesSet != publishedCertificates) {
                val record = Record(GATEWAY_TLS_TRUSTSTORES, groupId, GatewayTruststore(certificates))
                publisher.publish(
                    listOf(record)
                ).forEach {
                    it.join()
                }
            }
            certificatesSet
        }
    }
}
