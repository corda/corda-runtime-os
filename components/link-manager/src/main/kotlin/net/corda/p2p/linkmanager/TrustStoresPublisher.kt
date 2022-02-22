package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@Suppress("LongParameterList")
internal class TrustStoresPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configuration: SmartConfig,
    instanceId: Int,
) : LifecycleWithDominoTile, NetworkMapListener {

    companion object {
        val logger = contextLogger()
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_truststore_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_truststore_writer"
    }

    private val publishedGroups = ConcurrentHashMap<String, Set<PemCertificates>>()
    private val toPublish = ConcurrentLinkedDeque<NetworkMapListener.GroupInfo>()
    private val ready = CompletableFuture<Unit>()
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(MISSING_DATA_WRITER_GROUP_NAME),
        configuration,
    )
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_TLS_TRUSTSTORES, instanceId),
        Processor(),
        configuration,
    )
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    override fun groupAdded(groupInfo: NetworkMapListener.GroupInfo) {
        toPublish.offerLast(groupInfo)
        publishQueueIfPossible()
    }

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(
            publisher.dominoTile,
            subscriptionTile
        ),
        dependentChildren = listOf(
            publisher.dominoTile,
            subscriptionTile
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
            publishQueueIfPossible()
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

    private fun createResources(
        @Suppress("UNUSED_PARAMETER") resourcesHolder: ResourcesHolder
    ): CompletableFuture<Unit> {
        return ready
    }

    private fun publishQueueIfPossible() {
        while (ready.isDone) {
            val groupInfo = toPublish.pollFirst() ?: return
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
