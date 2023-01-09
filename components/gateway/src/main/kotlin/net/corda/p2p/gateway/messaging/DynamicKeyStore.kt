package net.corda.p2p.gateway.messaging

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.delegated.signing.Alias
import net.corda.crypto.delegated.signing.CertificateChain
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SignatureSpec
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
internal class DynamicKeyStore(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    private val cryptoOpsClient: CryptoOpsClient,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
    private val keyStoreFactory: (
        DelegatedSigner,
        DelegatedCertificateStore
    ) -> KeyStoreFactory = { signer, certificatesStore ->  KeyStoreFactory(signer, certificatesStore) },
) : DelegatedCertificateStore, LifecycleWithDominoTile, DelegatedSigner {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_certificates_truststores_reader"
        private val logger = contextLogger()
    }
    override val aliasToCertificates = ConcurrentHashMap<Alias, CertificateChain>()

    private val publicKeyToTenantId = ConcurrentHashMap<PublicKey, String>()

    private val holdingIdentityToClientKeyStore = ConcurrentHashMap<HoldingIdentity, ClientKeyStore>()

    val serverKeyStore by lazy {
        keyStoreFactory(this, this).createDelegatedKeyStore()
    }

    private inner class ClientKeyStore(
        private val certificates: CertificateChain,
        private val tenantId: String,
    ): DelegatedCertificateStore, DelegatedSigner {
        val keyStore by lazy {
            keyStoreFactory(this, this).createDelegatedKeyStore()
        }
        override val aliasToCertificates: Map<Alias, CertificateChain> = mapOf(tenantId to certificates)

        private val expectedPublicKey by lazy {
            certificates.firstOrNull()?.publicKey
        }

        override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
            if(publicKey != expectedPublicKey) {
                throw InvalidKeyException("Unknown public key")
            }
            return cryptoOpsClient.sign(tenantId, publicKey, spec, data).bytes
        }
    }

    fun getClientKeyStore(clientIdentity: HoldingIdentity) : KeyStoreWithPassword?  =
        holdingIdentityToClientKeyStore[clientIdentity]?.keyStore

    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_CERTIFICATES)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration
        )
    }

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )

    private val ready = CompletableFuture<Unit>()

    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            subscriptionTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            blockingDominoTile.coordinatorName
        ),
        managedChildren = listOf(
            subscriptionTile.toNamedLifecycle(),
            NamedLifecycle.of(cryptoOpsClient),
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
                        holdingIdentityToClientKeyStore[entry.value.holdingIdentity] = ClientKeyStore(
                            certificates,
                            entry.value.tenantId,
                        )
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
                if(oldValue != null) {
                    holdingIdentityToClientKeyStore.remove(oldValue.holdingIdentity)
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
                    holdingIdentityToClientKeyStore[chain.holdingIdentity] = ClientKeyStore(
                        certificates,
                        chain.tenantId,
                    )
                }
                logger.info("TLS certificate updated for the following identities: ${currentData.keys}")
            }
        }
    }

    override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        val tenantId = publicKeyToTenantId[publicKey] ?: throw InvalidKeyException("Unknown public key")
        return cryptoOpsClient.sign(tenantId, publicKey, spec, data).bytes
    }
}
