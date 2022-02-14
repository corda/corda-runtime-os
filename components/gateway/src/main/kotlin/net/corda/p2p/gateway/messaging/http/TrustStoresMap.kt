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

internal class TrustStoresMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) :
    LifecycleWithDominoTile {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_tls_truststores_reader"
    }

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources
    )

    private val ready = AtomicReference<CompletableFuture<Unit>>()

    private val processor = object : CompactedProcessor<String, GatewayTruststore> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTruststore::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTruststore>) {
            groupIdToTrustroots.putAll(
                currentData.mapValues {
                    TrustedCertificates(it.value.trustedCertificates, certificateFactory)
                }
            )
            ready.get()?.complete(Unit)
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
                groupIdToTrustroots[newRecord.key] = store
            } else {
                groupIdToTrustroots.remove(newRecord.key)
            }
        }
    }

    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_TRUSTSTORES, instanceId),
        processor,
        nodeConfiguration
    )

    private val groupIdToTrustroots = ConcurrentHashMap<String, TrustedCertificates>()

    fun getTrustStore(groupId: String) =
        groupIdToTrustroots[groupId]
            ?.trustStore
            ?: throw IllegalArgumentException("Unknown trust store: $groupId")

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val resourceFuture = CompletableFuture<Unit>()
        ready.set(resourceFuture)
        subscription.start()
        resources.keep { subscription.stop() }
        return resourceFuture
    }

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
}
