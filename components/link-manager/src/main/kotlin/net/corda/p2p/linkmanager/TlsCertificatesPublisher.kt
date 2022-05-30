package net.corda.p2p.linkmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import net.corda.data.identity.HoldingIdentity
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
import net.corda.p2p.GatewayTlsCertificates
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES

@Suppress("LongParameterList")
internal class TlsCertificatesPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile, HostingMapListener {

    companion object {
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_tlscertificates_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_tlscertificates_writer"
    }

    private val publishedIds = ConcurrentHashMap<String, Set<PemCertificates>>()
    private val toPublish = ConcurrentLinkedQueue<HostingMapListener.IdentityInfo>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        registry,
        PublisherConfig(MISSING_DATA_WRITER_GROUP_NAME, false),
        messagingConfiguration,
    )

    private fun HoldingIdentity.asString(): String {
        return "${this.groupId}-${this.x500Name}"
    }

    private fun publishIfNeeded(identity: HostingMapListener.IdentityInfo) {
        publishedIds.compute(identity.holdingIdentity.asString()) { id, publishedCertificates ->
            val certificatesSet = identity.tlsCertificates.toSet()
            if (certificatesSet != publishedCertificates) {
                val record = Record(
                    GATEWAY_TLS_CERTIFICATES, id,
                    GatewayTlsCertificates(
                        identity.tlsTenantId,
                        identity.tlsCertificates
                    )
                )
                publisher.publish(
                    listOf(record)
                ).forEach {
                    it.join()
                }
            }
            certificatesSet
        }
    }

    private val ready = CompletableFuture<Unit>()

    override fun identityAdded(identityInfo: HostingMapListener.IdentityInfo) {
        toPublish.offer(identityInfo)
        if (dominoTile.isRunning) publishQueue()
    }

    private inner class Processor : CompactedProcessor<String, GatewayTlsCertificates> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTlsCertificates::class.java
        override fun onSnapshot(currentData: Map<String, GatewayTlsCertificates>) {
            publishedIds.putAll(
                currentData.mapValues {
                    it.value.tlsCertificates.toSet()
                }
            )
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTlsCertificates>,
            oldValue: GatewayTlsCertificates?,
            currentData: Map<String, GatewayTlsCertificates>,
        ) {
            val certificates = newRecord.value?.tlsCertificates
            if (certificates == null) {
                publishedIds.remove(newRecord.key)
            } else {
                publishedIds[newRecord.key] = certificates.toSet()
            }
        }
    }
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_TLS_CERTIFICATES),
        Processor(),
        messagingConfiguration,
    )
    private val subscriptionDominoTile = SubscriptionDominoTile(
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

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        registry,
        onStart = ::onStart,
        managedChildren = listOf(
            publisher.dominoTile,
            subscriptionDominoTile,
            blockingDominoTile
        ),
        dependentChildren = listOf(
            publisher.dominoTile.coordinatorName,
            subscriptionDominoTile.coordinatorName,
            blockingDominoTile.coordinatorName
        )
    )

    private fun onStart() {
        publishQueue()
    }

    private fun publishQueue() {
        while (true) {
            val identityInfo = toPublish.poll() ?: return
            publishIfNeeded(identityInfo)
        }
    }
}
