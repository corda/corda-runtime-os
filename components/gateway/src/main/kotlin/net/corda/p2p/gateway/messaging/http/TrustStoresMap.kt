package net.corda.p2p.gateway.messaging.http

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger

internal class TrustStoresMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) :
    LifecycleWithDominoTile {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_tls_truststores_reader"
        private val logger = contextLogger()
    }
    private val ready = CompletableFuture<Unit>()
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_TRUSTSTORES),
        Processor(),
        messagingConfiguration
    )

    /**
     * We maintain the entries from the compacted topic per key in-memory, so that we can easily delete entries.
     * We also maintain a secondary collection of certificates indexed by holding identity for fast lookups.
     */
    private val entriesPerKey = ConcurrentHashMap<String, GatewayTruststore>()
    private val trustRootsPerHoldingIdentity = ConcurrentHashMap<TruststoreKey, TrustedCertificates>()

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList()
    )

    fun getTrustStore(sourceX500Name: String, destinationGroupId: String) =
        trustRootsPerHoldingIdentity[TruststoreKey(sourceX500Name, destinationGroupId)]
            ?.trustStore
            ?: throw IllegalArgumentException("Unknown trust store for source X500 name ($sourceX500Name) " +
                    "and group ID ($destinationGroupId)")

    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(subscriptionTile.coordinatorName, blockingDominoTile.coordinatorName),
        managedChildren = listOf(subscriptionTile.toNamedLifecycle(), blockingDominoTile.toNamedLifecycle()),
    )

    class TrustedCertificates(
        pemCertificates: Collection<String>,
        certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
    ) {

        val trustStore: KeyStore by lazy {
            KeyStore.getInstance("PKCS12").also { keyStore ->
                keyStore.load(null, null)
                pemCertificates.withIndex().forEach { (index, pemCertificate) ->
                    val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                    keyStore.setCertificateEntry("gateway-$index", certificate)
                }
            }
        }
    }

    private inner class Processor : CompactedProcessor<String, GatewayTruststore> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            entriesPerKey.putAll(currentData)
            val newTrustRoots = currentData.map {
                val truststoreKey = TruststoreKey(it.value.sourceIdentity.x500Name, it.value.sourceIdentity.groupId)
                truststoreKey to TrustedCertificates(it.value.trustedCertificates, certificateFactory)
            }.toMap()
            trustRootsPerHoldingIdentity.putAll(newTrustRoots)
            logger.info("Received initial set of trust roots for the following holding x500 names and destination groups: " +
                    "${newTrustRoots.keys}")
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTruststore>,
            oldValue: GatewayTruststore?,
            currentData: Map<String, GatewayTruststore>,
        ) {
            if (newRecord.value != null) {
                entriesPerKey[newRecord.key] = newRecord.value!!
                val truststoreKey = TruststoreKey(newRecord.value!!.sourceIdentity.x500Name, newRecord.value!!.sourceIdentity.groupId)
                val trustedCertificates = TrustedCertificates(newRecord.value!!.trustedCertificates, certificateFactory)
                trustRootsPerHoldingIdentity[truststoreKey] = trustedCertificates
                logger.info("Trust roots updated for x500 name ${truststoreKey.sourceX500Name} and " +
                        "group ID ${truststoreKey.destinationGroupId}.")
            } else {
                val previousRecord = entriesPerKey.remove(newRecord.key)
                if (previousRecord != null) {
                    val truststoreKey = TruststoreKey(previousRecord.sourceIdentity.x500Name, previousRecord.sourceIdentity.groupId)
                    trustRootsPerHoldingIdentity.remove(truststoreKey)
                    logger.info("Trust roots removed for x500 name ${truststoreKey.sourceX500Name} and " +
                            "group ID ${truststoreKey.destinationGroupId}.")
                }
            }
        }
    }

    private data class TruststoreKey(val sourceX500Name: String, val destinationGroupId: String)
}