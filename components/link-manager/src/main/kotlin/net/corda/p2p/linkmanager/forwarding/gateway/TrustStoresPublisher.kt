package net.corda.p2p.linkmanager.forwarding.gateway

import net.corda.crypto.utils.PemCertificate
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("LongParameterList")
internal class TrustStoresPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile {

    companion object {
        val logger = contextLogger()
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_truststore_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_truststore_writer"
    }

    private val publishedGroups = ConcurrentHashMap<String, Set<PemCertificate>>()
    private val toPublish = ConcurrentLinkedQueue<GatewayTruststore>()
    private val ready = CompletableFuture<Unit>()
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(MISSING_DATA_WRITER_GROUP_NAME, false),
        messagingConfiguration,
    )
    private val subscriptionConfig = SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_TLS_TRUSTSTORES)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration,
        )
    }
    private val subscriptionTile = SubscriptionDominoTile(
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

    fun groupAdded(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy) {
        toPublish.offer(
            GatewayTruststore(
                holdingIdentity.toAvro(),
                groupPolicy.p2pParameters.tlsTrustRoots.toList()
            )
        )
        publishQueueIfPossible()
    }

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        onStart = ::onStart,
        managedChildren = listOf(
            publisher.dominoTile.toNamedLifecycle(),
            subscriptionTile.toNamedLifecycle(),
            blockingDominoTile.toNamedLifecycle()
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

    private fun onStart() {
        publishQueueIfPossible()
    }

    private fun publishQueueIfPossible() {
        while ((publisher.isRunning) && (ready.isDone)) {
            val groupInfo = toPublish.poll() ?: return
            publishGroupIfNeeded(groupInfo)
        }
    }

    private fun publishGroupIfNeeded(gatewayTruststore: GatewayTruststore) {
        val holdingIdentity = gatewayTruststore.sourceIdentity.toCorda()
        publishedGroups.compute(holdingIdentity.toKafkaKey()) { _, publishedCertificates ->
            logger.info("Publishing trust roots for $holdingIdentity to the gateway.")
            val certificatesSet = gatewayTruststore.trustedCertificates.toSet()
            if (certificatesSet != publishedCertificates) {
                val record = Record(
                    GATEWAY_TLS_TRUSTSTORES,
                    holdingIdentity.toKafkaKey(),
                    gatewayTruststore,
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

    private fun HoldingIdentity.toKafkaKey() = "${x500Name}-${groupId}"
}
