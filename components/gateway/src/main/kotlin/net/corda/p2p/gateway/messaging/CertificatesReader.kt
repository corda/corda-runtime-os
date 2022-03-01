package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.Alias
import net.corda.crypto.delegated.signing.CertificateChain
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.schema.Schemas
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class CertificatesReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) : DelegatedCertificateStore, LifecycleWithDominoTile {
    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_certificates_truststores_reader"
    }
    override val aliasToCertificates = ConcurrentHashMap<Alias, CertificateChain>()

    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_CERTIFICATES, instanceId),
        Processor(),
        nodeConfiguration
    )

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(subscriptionTile),
        dependentChildren = listOf(subscriptionTile),
    )

    private val ready = CompletableFuture<Unit>()

    private inner class Processor : CompactedProcessor<String, GatewayTlsCertificates> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTlsCertificates::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTlsCertificates>) {
            aliasToCertificates.putAll(
                currentData.mapValues { entry ->
                    entry.value.tlsCertificates.map { pemCertificate ->
                        ByteArrayInputStream(pemCertificate.toByteArray()).use {
                            certificateFactory.generateCertificate(it)
                        }
                    }
                }
            )
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTlsCertificates>,
            oldValue: GatewayTlsCertificates?,
            currentData: Map<String, GatewayTlsCertificates>,
        ) {
            val chain = newRecord.value
            if (chain == null) {
                aliasToCertificates.remove(newRecord.key)
            } else {
                aliasToCertificates[newRecord.key] = chain.tlsCertificates.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                }
            }
        }
    }

    private fun createResources(
        @Suppress("UNUSED_PARAMETER")
        resources: ResourcesHolder
    ): CompletableFuture<Unit> {
        return ready
    }
}
