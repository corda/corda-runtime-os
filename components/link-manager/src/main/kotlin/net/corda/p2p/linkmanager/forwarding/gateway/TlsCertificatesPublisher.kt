package net.corda.p2p.linkmanager.forwarding.gateway

import net.corda.crypto.utils.PemCertificate
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.linkmanager.hosting.HostingMapListener
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("LongParameterList")
internal class TlsCertificatesPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile, HostingMapListener {

    companion object {
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_tlscertificates_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_tlscertificates_writer"
    }

    private val publishedIds = ConcurrentHashMap<String, Set<PemCertificate>>()
    private val toPublish = ConcurrentLinkedQueue<HostingMapListener.IdentityInfo>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
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
                        identity.holdingIdentity.toAvro(),
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
        publishQueueIfPossible()
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
            publishQueueIfPossible()
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
    private val subscriptionConfig = SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_TLS_CERTIFICATES)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration,
        )
    }
    private val subscriptionDominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
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
        onStart = ::onStart,
        dependentChildren = listOf(
            publisher.dominoTile.coordinatorName,
            subscriptionDominoTile.coordinatorName,
            blockingDominoTile.coordinatorName
        ),
        managedChildren = listOf(
            publisher.dominoTile.toNamedLifecycle(),
            subscriptionDominoTile.toNamedLifecycle(),
            blockingDominoTile.toNamedLifecycle()
        )
    )

    private fun onStart() {
        publishQueueIfPossible()
    }

    private fun publishQueueIfPossible() {
        while ((publisher.isRunning) && (ready.isDone)) {
            val identityInfo = toPublish.poll() ?: return
            publishIfNeeded(identityInfo)
        }
    }
}
