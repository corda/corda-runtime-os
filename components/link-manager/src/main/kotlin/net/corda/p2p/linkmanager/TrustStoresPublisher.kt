package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
class TrustStoresPublisher(
    private val subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val linkManagerNetworkMap: LinkManagerNetworkMap,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val configuration: SmartConfig,
    private val instanceId: Int,
) : LifecycleWithDominoTile {

    companion object {
        private const val READ_CURRENT_DATA = "linkmanager_truststore_reader"
        private const val WRITE_MISSING_DATA = "linkmanager_truststore_writer"
    }

    private val publishedGroups = ConcurrentHashMap.newKeySet<String>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(WRITE_MISSING_DATA),
        configuration,
    )

    fun publishGroupIfNeeded(groupId: String) {
        if ((!publishedGroups.contains(groupId)) && (
            linkManagerHostingMap.locallyHostedIdentities.any { it.groupId == groupId }
            )
        ) {
            val certificates = linkManagerNetworkMap.getTrustedCertificates(groupId) ?: return
            val record = Record(GATEWAY_TLS_TRUSTSTORES, groupId, GatewayTruststore(certificates))
            publisher.publish(
                listOf(record)
            ).forEach {
                it.join()
            }
            publishedGroups += groupId
        }
    }

    private inner class PublishedDataProcessor : CompactedProcessor<String, GatewayTruststore> {
        val ready = CompletableFuture<Unit>()
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java
        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            publishedGroups.addAll(currentData.keys)
            linkManagerHostingMap.locallyHostedIdentities
                .map { it.groupId }
                .toSet()
                .forEach {
                    publishGroupIfNeeded(it)
                }
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTruststore>,
            oldValue: GatewayTruststore?,
            currentData: Map<String, GatewayTruststore>,
        ) {
            if (newRecord.value == null) {
                publishedGroups.remove(newRecord.key)
            } else {
                publishedGroups += newRecord.key
            }
        }
    }

    override val dominoTile = DominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        dependentChildren = listOf(
            linkManagerNetworkMap.dominoTile,
            linkManagerHostingMap.dominoTile,
            publisher.dominoTile,
        )
    )

    private fun createResources(resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
        val processor = PublishedDataProcessor()
        val subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(READ_CURRENT_DATA, GATEWAY_TLS_TRUSTSTORES, instanceId),
            processor,
            configuration,
        )
        resourcesHolder.keep(subscription)
        subscription.start()

        return processor.ready
    }
}
