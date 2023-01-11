package net.corda.p2p.gateway.messaging.http

import net.corda.crypto.utils.convertToKeyStore
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
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

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
    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_TRUSTSTORES)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration
        )
    }

    private val entriesPerKey = ConcurrentHashMap<String, TruststoreKey>()
    private val trustRootsPerHoldingIdentity = ConcurrentHashMap<TruststoreKey, TrustedCertificates>()

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList()
    )

    fun getTrustStore(sourceX500Name: MemberX500Name, destinationGroupId: String) =
        trustRootsPerHoldingIdentity[TruststoreKey(sourceX500Name, destinationGroupId)]
            ?.trustStore
            ?: throw IllegalArgumentException("Unknown trust store for source X500 name ($sourceX500Name) " +
                    "and group ID ($destinationGroupId)")

    fun getTrustStores() = trustRootsPerHoldingIdentity.values.mapNotNull { it.trustStore }

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
        val trustStore: KeyStore? by lazy {
            convertToKeyStore(certificateFactory, pemCertificates, "gateway")
        }
    }

    private inner class Processor : CompactedProcessor<String, GatewayTruststore> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            // Using source group ID here, since source and identity are expected to be in the same group.
            val newEntriesPerKey = currentData.mapValues {
                createKey(it.value.sourceIdentity.x500Name, it.value.sourceIdentity.groupId)
            }
            entriesPerKey.putAll(newEntriesPerKey)

            val newTrustRoots = currentData.map {
                val truststoreKey = createKey(it.value.sourceIdentity.x500Name, it.value.sourceIdentity.groupId)
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
            val value = newRecord.value

            if (value != null) {
                val truststoreKey = createKey(value.sourceIdentity.x500Name, value.sourceIdentity.groupId)
                entriesPerKey[newRecord.key] = truststoreKey
                val trustedCertificates = TrustedCertificates(value.trustedCertificates, certificateFactory)
                trustRootsPerHoldingIdentity[truststoreKey] = trustedCertificates
                logger.info("Trust roots updated for x500 name ${truststoreKey.sourceX500Name} and " +
                        "group ID ${truststoreKey.destinationGroupId}.")
            } else {
                val truststoreKey = entriesPerKey.remove(newRecord.key)
                if (truststoreKey != null) {
                    trustRootsPerHoldingIdentity.remove(truststoreKey)
                    logger.info("Trust roots removed for x500 name ${truststoreKey.sourceX500Name} and " +
                            "group ID ${truststoreKey.destinationGroupId}.")
                }
            }
        }
    }

    private fun createKey(sourceX500Name: String, destinationGroupId: String) : TruststoreKey {
        return TruststoreKey(
            MemberX500Name.parse(sourceX500Name),
            destinationGroupId,
        )
    }

    private data class TruststoreKey(val sourceX500Name: MemberX500Name, val destinationGroupId: String)
}
