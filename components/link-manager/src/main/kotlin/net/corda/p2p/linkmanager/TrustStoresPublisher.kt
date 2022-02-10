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
internal class TrustStoresPublisher(
    private val subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configuration: SmartConfig,
    private val instanceId: Int,
) : LifecycleWithDominoTile {

    companion object {
        private const val READ_CURRENT_DATA = "linkmanager_truststore_reader"
        private const val WRITE_MISSING_DATA = "linkmanager_truststore_writer"
    }

    private val publishedGroups = ConcurrentHashMap<String, List<String>>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(WRITE_MISSING_DATA),
        configuration,
    )

    fun publishGroupIfNeeded(groupId: String, certificates: List<String>) {
        publishedGroups.compute(groupId) { _, publishedCertificates ->
            if (certificates != publishedCertificates) {
                val record = Record(GATEWAY_TLS_TRUSTSTORES, groupId, GatewayTruststore(certificates))
                publisher.publish(
                    listOf(record)
                ).forEach {
                    it.join()
                }
            }
            certificates
        }
    }

    private inner class PublishedDataProcessor : CompactedProcessor<String, GatewayTruststore> {
        val ready = CompletableFuture<Unit>()
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java
        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            publishedGroups.putAll(
                currentData.mapValues {
                    it.value.trustedCertificates
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
                publishedGroups.put(newRecord.key, certificates)
            }
        }
    }

    override val dominoTile = DominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(
            publisher.dominoTile,
        )
    )

    fun createResources(resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
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
