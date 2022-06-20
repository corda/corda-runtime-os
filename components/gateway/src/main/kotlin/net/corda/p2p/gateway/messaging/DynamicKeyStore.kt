package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.impl.CryptoOpsClientComponent
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.delegated.signing.Alias
import net.corda.crypto.delegated.signing.CertificateChain
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SignatureSpec

@Suppress("LongParameterList")
internal class DynamicKeyStore(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    private val configurationReaderService: ConfigurationReadService,
    private val signingMode: SigningMode,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) : DelegatedCertificateStore, LifecycleWithDominoTile, DelegatedSigner {
    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_certificates_truststores_reader"
        private val logger = contextLogger()
    }
    override val aliasToCertificates = ConcurrentHashMap<Alias, CertificateChain>()

    private val publicKeyToTenantId = ConcurrentHashMap<PublicKey, String>()

    val keyStore by lazy {
        KeyStoreFactory(this, this).createDelegatedKeyStore()
    }

    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_CERTIFICATES),
        Processor(),
        messagingConfiguration
    )

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    private val ready = CompletableFuture<Unit>()

    private val stubSigner = if (signingMode == SigningMode.STUB) {
        StubCryptoProcessor(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfiguration,
        )
    } else {
        null
    }
    private val cryptoOpsClient: CryptoOpsClient? = if (signingMode == SigningMode.REAL) {
        CryptoOpsClientComponent(lifecycleCoordinatorFactory, publisherFactory, CipherSchemeMetadataImpl(), configurationReaderService)
    } else {
        null
    }

    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    private val signerCoordinatorName = if (signingMode == SigningMode.REAL) {
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    } else {
        stubSigner!!.dominoTile.coordinatorName
    }
    private val signerNamedLifecycle = if (signingMode == SigningMode.REAL) {
        NamedLifecycle(cryptoOpsClient!!, signerCoordinatorName)
    } else {
        stubSigner!!.dominoTile.toNamedLifecycle()
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            subscriptionTile.coordinatorName,
            signerCoordinatorName,
            blockingDominoTile.coordinatorName
        ),
        managedChildren = listOf(
            subscriptionTile.toNamedLifecycle(),
            signerNamedLifecycle,
            blockingDominoTile.toNamedLifecycle()
        ),
    )

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
                    }.also { certificates ->
                        certificates.firstOrNull()?.publicKey?.also { publicKey ->
                            publicKeyToTenantId[publicKey] = entry.value.tenantId
                        }
                    }
                }
            )
            logger.info("Received initial set of TLS certificates for the following identities: ${currentData.keys}.")
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayTlsCertificates>,
            oldValue: GatewayTlsCertificates?,
            currentData: Map<String, GatewayTlsCertificates>,
        ) {
            val chain = newRecord.value
            if (chain == null) {
                aliasToCertificates.remove(newRecord.key)?.also { certificates ->
                    certificates.firstOrNull()?.publicKey?.also { publicKey ->
                        publicKeyToTenantId.remove(publicKey)
                    }
                }
                logger.info("TLS certificate removed for the following identities: ${currentData.keys}.")
            } else {
                aliasToCertificates[newRecord.key] = chain.tlsCertificates.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                }.also { certificates ->
                    certificates.firstOrNull()?.publicKey?.also { publicKey ->
                        publicKeyToTenantId[publicKey] = chain.tenantId
                    }
                }
                logger.info("TLS certificate updated for the following identities: ${currentData.keys}")
            }
        }
    }

    override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        val tenantId = publicKeyToTenantId[publicKey] ?: throw InvalidKeyException("Unknown public key")
        return if (signingMode == SigningMode.REAL) {
            cryptoOpsClient!!.sign(tenantId, publicKey, spec, data).bytes
        } else {
            stubSigner!!.sign(tenantId, publicKey, spec, data)
        }
    }
}

/**
 * This switch will exist temporarily until we complete migration with the membership components (and dynamic networks).
 * After that point, it can be removed as only the real crypto processor will be used.
 */
enum class SigningMode {
    /**
     * In this mode, signing is delegated to a real crypto processor.
     */
    REAL,

    /**
     * In this mode, signing is delegated to a stub crypto processor (that reads cryptographic material directly from Kafka)
     */
    STUB
}