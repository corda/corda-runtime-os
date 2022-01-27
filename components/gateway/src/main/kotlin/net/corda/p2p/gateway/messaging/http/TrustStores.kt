package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
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
import java.util.concurrent.atomic.AtomicReference

internal class TrustStores(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
) :
    CompactedProcessor<String, GatewayTruststore>,
    LifecycleWithDominoTile {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_tls_truststores"
    }

    override val keyClass = String::class.java
    override val valueClass = GatewayTruststore::class.java

    class Truststore(pemCertificates: Collection<String>) {

        val trustStore: KeyStore by lazy {
            KeyStore.getInstance("JKS").also { keyStore ->
                keyStore.load(null, null)
                pemCertificates.withIndex().forEach { (index, pemCertificate) ->
                    val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        CertificateFactory.getInstance("X.509")
                            .generateCertificate(it)
                    }
                    keyStore.setCertificateEntry("gateway-$index", certificate)
                }
            }
        }
    }

    private val hashToActualStore = ConcurrentHashMap<String, Truststore>()

    override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
        hashToActualStore.putAll(
            currentData.mapValues {
                Truststore(it.value.trustedCertificates)
            }
        )
        ready.get()?.complete(Unit)
    }

    fun trustStore(hash: String) = hashToActualStore[hash]?.trustStore ?: throw IllegalArgumentException("Unknown trust store: $hash")

    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, instanceId),
        this,
        nodeConfiguration
    )

    override fun onNext(
        newRecord: Record<String, GatewayTruststore>,
        oldValue: GatewayTruststore?,
        currentData: Map<String, GatewayTruststore>,
    ) {
        val store = newRecord.value?.let {
            Truststore(it.trustedCertificates)
        }

        if (store != null) {
            hashToActualStore[newRecord.key] = store
        } else {
            hashToActualStore.remove(newRecord.key)
        }
    }

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources
    )

    private val ready = AtomicReference<CompletableFuture<Unit>>()

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val resourceFuture = CompletableFuture<Unit>()
        ready.set(resourceFuture)
        subscription.start()
        resources.keep { subscription.stop() }
        return resourceFuture
    }
}
