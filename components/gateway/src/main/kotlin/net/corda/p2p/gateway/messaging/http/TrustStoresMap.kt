package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class TrustStoresMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) :
    LifecycleWithDominoTile {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_tls_truststores_reader"
    }
    private val ready = CompletableFuture<Unit>()
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_TRUSTSTORES),
        Processor(),
        nodeConfiguration
    )
    private val groupIdToTrustRoots = ConcurrentHashMap<String, TrustedCertificates>()
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList()
    )

    fun getTrustStore(groupId: String) =
        groupIdToTrustRoots[groupId]
            ?.trustStore
            ?: throw IllegalArgumentException("Unknown trust store: $groupId")

    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        registry,
        managedChildren = listOf(subscriptionTile, blockingDominoTile),
        dependentChildren = listOf(subscriptionTile, blockingDominoTile),
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
            groupIdToTrustRoots.putAll(
                currentData.mapValues {
                    TrustedCertificates(it.value.trustedCertificates, certificateFactory)
                }
            )
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTruststore>,
            oldValue: GatewayTruststore?,
            currentData: Map<String, GatewayTruststore>,
        ) {
            val store = newRecord.value?.let {
                TrustedCertificates(it.trustedCertificates, certificateFactory)
            }

            if (store != null) {
                groupIdToTrustRoots[newRecord.key] = store
            } else {
                groupIdToTrustRoots.remove(newRecord.key)
            }
        }
    }
}
