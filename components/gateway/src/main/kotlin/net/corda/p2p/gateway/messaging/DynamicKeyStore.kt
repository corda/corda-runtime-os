package net.corda.p2p.gateway.messaging

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.delegated.signing.Alias
import net.corda.crypto.delegated.signing.CertificateChain
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
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
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
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
    signingMode: SigningMode,
    private val cryptoOpsClient: CryptoOpsClient,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) : DelegatedCertificateStore, LifecycleWithDominoTile, DelegatedSigner {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_certificates_truststores_reader"
        private val logger = contextLogger()
    }
    override val aliasToCertificates = ConcurrentHashMap<Alias, CertificateChain>()

    private val publicKeyToTenantId = ConcurrentHashMap<PublicKey, String>()

    private val clientCertificates = ConcurrentHashMap<String, ClientCertificates>()

    val keyStore by lazy {
        KeyStoreFactory(this, this).createDelegatedKeyStore()
    }

    private inner class ClientCertificates(
        certificates: CertificateChain,
        private val tenantId: String
    ) : DelegatedCertificateStore, DelegatedSigner {
        override val aliasToCertificates: Map<Alias, CertificateChain> = mapOf(
            "client-certificate" to certificates
        )

        override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
            return signer.sign(tenantId, publicKey, spec, data)
        }

        val keyStoreWithPassword by lazy {
            KeyStoreFactory(
                this,
                this,
            ).createDelegatedKeyStore()
        }
    }

    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_TLS_CERTIFICATES)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration
        )
    }

    fun createKeyStoreForClient(sourceX500Name: MemberX500Name, destinationGroupId: String): KeyStoreWithPassword? {
        return clientCertificates["$destinationGroupId-$sourceX500Name"]?.keyStoreWithPassword
    }

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )

    private val ready = CompletableFuture<Unit>()

    private val signer: CryptoProcessor = if (signingMode == SigningMode.REAL) {
      object: CryptoProcessor {
          override fun sign(tenantId: String, publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
              return cryptoOpsClient.sign(tenantId, publicKey, spec, data).bytes
          }

          override val namedLifecycle = NamedLifecycle(cryptoOpsClient, LifecycleCoordinatorName.forComponent<CryptoOpsClient>())
      }
    } else {
        StubCryptoProcessor(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfiguration,
        )
    }

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
            signer.namedLifecycle.name,
            blockingDominoTile.coordinatorName
        ),
        managedChildren = listOf(
            subscriptionTile.toNamedLifecycle(),
            signer.namedLifecycle,
            blockingDominoTile.toNamedLifecycle()
        ),
    )

    private inner class Processor : CompactedProcessor<String, GatewayTlsCertificates> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTlsCertificates::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTlsCertificates>) {
            aliasToCertificates.putAll(
                currentData.mapValues { entry ->
                    entry.value.serverTlsCertificates.map { pemCertificate ->
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
            currentData.forEach { (alias, value) ->
                value.clientTlsCertificates?.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                }?.also { certificates ->
                    clientCertificates[alias] = ClientCertificates(certificates, value.tenantId)
                }
            }

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
                clientCertificates.remove(newRecord.key)
                logger.info("TLS certificate removed for the following identities: ${currentData.keys}.")
            } else {
                aliasToCertificates[newRecord.key] = chain.serverTlsCertificates.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                }.also { certificates ->
                    certificates.firstOrNull()?.publicKey?.also { publicKey ->
                        publicKeyToTenantId[publicKey] = chain.tenantId
                    }
                }

                val certificates = chain.clientTlsCertificates?.map { pemCertificate ->
                    ByteArrayInputStream(pemCertificate.toByteArray()).use {
                        certificateFactory.generateCertificate(it)
                    }
                }
                if (certificates != null) {
                    clientCertificates[newRecord.key] = ClientCertificates(certificates, chain.tenantId)
                }

                logger.info("TLS certificate updated for the following identities: ${currentData.keys}")
            }
        }
    }

    override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        val tenantId = publicKeyToTenantId[publicKey] ?: throw InvalidKeyException("Unknown public key")
        return signer.sign(tenantId, publicKey, spec, data)
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
