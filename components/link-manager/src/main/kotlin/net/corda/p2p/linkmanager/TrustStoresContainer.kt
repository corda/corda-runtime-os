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
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.v5.base.util.toBase64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
class TrustStoresContainer(
    private val subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val linkManagerNetworkMap: LinkManagerNetworkMap,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val configuration: SmartConfig,
    private val instanceId: Int,
) : LifecycleWithDominoTile {

    /**
     * Return a hash of the identity trust store.
     * If the identity is not locally hosted, will return null.
     */
    fun computeTrustStoreHash(identity: LinkManagerNetworkMap.HoldingIdentity): String? {
        return groupIdToHash.compute(identity.groupId) { groupId, hash ->
            if ((hash == null) &&
                (linkManagerHostingMap.locallyHostedIdentities.any { it.groupId == groupId })
            ) {
                generateHash(groupId)
            } else {
                hash
            }
        }
    }

    companion object {
        private const val READ_CURRENT_DATA = "linkmanager_truststore_reader"
        private const val WRITE_MISSING_DATA = "linkmanager_truststore_writer"
    }

    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

    private val groupIdToHash = ConcurrentHashMap<String, String>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(WRITE_MISSING_DATA),
        configuration,
    )

    private val publishedData = ConcurrentHashMap<String, GatewayTruststore>()
    private inner class PublishedDataProcessor : CompactedProcessor<String, GatewayTruststore> {
        val ready = CompletableFuture<Unit>()
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java
        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            publishedData.putAll(currentData)
            linkManagerHostingMap.locallyHostedIdentities.forEach {
                computeTrustStoreHash(it)
            }
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTruststore>,
            oldValue: GatewayTruststore?,
            currentData: Map<String, GatewayTruststore>,
        ) {
            val value = newRecord.value
            if (value == null) {
                publishedData.remove(newRecord.key)
            } else {
                publishedData[newRecord.key] = value
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

    private fun generateHash(groupId: String): String? {
        val certificates = linkManagerNetworkMap.getTrustedCertificates(groupId) ?: return null
        messageDigest.reset()
        certificates.forEach {
            messageDigest.update(it.toByteArray())
        }
        val hashBase = messageDigest.digest().toBase64()
        return generateSequence(1) { it + 1 }.map {
            "$hashBase-$it"
        }.map {
            it to publishedData[it]?.trustedCertificates
        }.mapNotNull { (key, value) ->
            if (value == null) {
                val record = Record(GATEWAY_TLS_TRUSTSTORES, key, GatewayTruststore(certificates))
                publisher.publish(
                    listOf(record)
                ).forEach {
                    it.join()
                }
                key
            } else {
                if (value == certificates) {
                    key
                } else {
                    null
                }
            }
        }.first()
    }

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
