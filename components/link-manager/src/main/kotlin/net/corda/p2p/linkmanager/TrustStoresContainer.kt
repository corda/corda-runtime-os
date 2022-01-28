package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toNetworkType
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.toBase64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
class TrustStoresContainer(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val linkManagerNetworkMap: LinkManagerNetworkMap,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val configuration: SmartConfig,
    private val instanceId: Int,
) : LifecycleWithDominoTile {
    companion object {
        private const val READ_CURRENT_DATA = "linkmanager_truststore_reader"
        private const val WRITE_MISSING_DATA = "linkmanager_truststore_writer"
        private val logger = contextLogger()
    }

    fun createLinkOutHeader(
        source: HoldingIdentity,
        destination: HoldingIdentity = source,
    ): LinkOutHeader? {
        return createLinkOutHeader(
            source.toHoldingIdentity(),
            destination.toHoldingIdentity(),
        )
    }

    fun createLinkOutHeader(
        source: LinkManagerNetworkMap.HoldingIdentity,
        destination: LinkManagerNetworkMap.HoldingIdentity = source,
    ): LinkOutHeader? {
        val destMemberInfo = linkManagerNetworkMap.getMemberInfo(destination)
        if (destMemberInfo == null) {
            logger.warn("Attempted to send message to peer $destination which is not in the network map. The message was discarded.")
            return null
        }
        val trustStoreHash = groupIdToHash[destination.groupId]
        if (trustStoreHash == null) {
            logger.warn("Attempted to send message to peer $destination which has no trust store. The message was discarded.")
            return null
        }

        val networkType = linkManagerNetworkMap.getNetworkType(source.groupId)
        if (networkType == null) {
            logger.warn("Could not find the network type in the NetworkMap for $source. The message was discarded.")
            return null
        }

        return LinkOutHeader(
            destMemberInfo.holdingIdentity.x500Name,
            networkType.toNetworkType(),
            destMemberInfo.endPoint.address,
            trustStoreHash
        )
    }

    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

    private val groupIdToHash = ConcurrentHashMap<String, String>()

    override val dominoTile = DominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        createResources = { createResources() },
        children = listOf(
            linkManagerNetworkMap.dominoTile,
            linkManagerHostingMap.dominoTile,
        )
    )

    fun createResources(): CompletableFuture<Unit> {
        return readCurrentData().thenApply { publishedData ->
            publishMissingCertificates(publishedData)
        }
    }

    private inner class PublishMissingCertificates(
        private val alreadyPublishedData: Map<String, GatewayTruststore>,
        private val identity: LinkManagerNetworkMap.HoldingIdentity,
    ) {
        private val groupId = identity.groupId
        private val certificates = linkManagerNetworkMap.getTrustedCertificates(groupId)
        var toPublish: Record<String, GatewayTruststore>? = null

        fun process() {
            if (certificates == null) {
                return
            }
            messageDigest.reset()
            certificates.forEach {
                messageDigest.update(it.toByteArray())
            }
            val hash = messageDigest.digest().toBase64()
            val data = generateSequence(1) { it + 1 }.map {
                "$hash-$it"
            }.map {
                it to alreadyPublishedData[it]?.trustedCertificates
            }.first { (key, value) ->
                if (value == null) {
                    toPublish = Record(GATEWAY_TLS_TRUSTSTORES, key, GatewayTruststore(certificates))
                    true
                } else {
                    value == certificates
                }
            }
            groupIdToHash[identity.groupId] = data.first
        }
    }

    private fun publishMissingCertificates(publishedData: Map<String, GatewayTruststore>) {
        val records = linkManagerHostingMap.locallyHostedIdentities.map {
            PublishMissingCertificates(publishedData, it)
        }.onEach {
            it.process()
        }.mapNotNull {
            it.toPublish
        }

        if (records.isNotEmpty()) {
            publisherFactory.createPublisher(
                PublisherConfig(WRITE_MISSING_DATA),
                configuration
            ).use { publisher ->
                publisher.publish(records)
                    .forEach {
                        it.join()
                    }
            }
        }
    }

    private fun readCurrentData(): CompletableFuture<Map<String, GatewayTruststore>> {
        val completed = CompletableFuture<Map<String, GatewayTruststore>>()
        val subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(READ_CURRENT_DATA, GATEWAY_TLS_TRUSTSTORES, instanceId),
            object : CompactedProcessor<String, GatewayTruststore> {
                override val keyClass = String::class.java
                override val valueClass = GatewayTruststore::class.java

                override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
                    completed.complete(currentData)
                }

                override fun onNext(
                    newRecord: Record<String, GatewayTruststore>,
                    oldValue: GatewayTruststore?,
                    currentData: Map<String, GatewayTruststore>,
                ) {
                    // Do nothing
                }
            },
            configuration
        )
        subscription.start()
        completed.whenComplete { _, _ ->
            subscription.stop()
        }

        return completed
    }
}
