package net.corda.p2p.gateway.messaging

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.delegated.signing.Alias
import net.corda.crypto.delegated.signing.CertificateChain
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.GatewayTlsCertificates
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
import net.corda.schema.Schemas
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.Objects
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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override val aliasToCertificates = ConcurrentHashMap<Alias, CertificateChain>()

    private val publicKeyToTenantId = ConcurrentHashMap<PublicKey, String>()

    private val holdingIdentityToClientKeyStore = ConcurrentHashMap<HoldingIdentity, ClientKeyStore>()

    val serverKeyStore by lazy {
        keyStoreFactory(this, this).createDelegatedKeyStore()
    }

    override fun logger(log: String) {
        logger.info("QQQ1 $log")
    }

    inner class ClientKeyStore(
        private val certificates: CertificateChain,
        private val tenantId: String,
    ): DelegatedCertificateStore, DelegatedSigner {
        override fun logger(log: String) {
            logger.info("QQQ2 $log")
        }
        val keyStore by lazy {
            keyStoreFactory(this, this).createDelegatedKeyStore()
        }
        override val aliasToCertificates: Map<Alias, CertificateChain> = mapOf(tenantId to certificates)

        private val expectedPublicKey by lazy {
            certificates.firstOrNull()?.publicKey
        }

        override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
            logger.info("QQQ trying to sign with $publicKey and $tenantId")
            logger.info("QQQ my expected public key is $expectedPublicKey")
            if(publicKey != expectedPublicKey) {
                logger.info("QQQ LOOK AT ME!")
                throw InvalidKeyException("Unknown public key")
            }
            return try {
                cryptoOpsClient.sign(tenantId, publicKey, spec, data).bytes
            }catch (e: Exception) {
                logger.info("QQQ LOOK AT ME 2", e)
                throw e
            }
        }

        override fun hashCode() = Objects.hash(certificates, tenantId)
        override fun equals(other: Any?) =
            ((other is ClientKeyStore) &&
                    (other.certificates == certificates) &&
                    (other.tenantId == tenantId))
    }

    fun getClientKeyStore(clientIdentity: HoldingIdentity) : ClientKeyStore?  =
        holdingIdentityToClientKeyStore[clientIdentity]

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

    private fun logMe(name: String, cert: GatewayTlsCertificates?) {
        logger.info("QQQ Got $name of key (server size)")
        if (cert == null) {
            logger.info("QQQ \t removing $name")
        } else {
            logger.info("QQQ \t tenant ID is ${cert.tenantId}")
            logger.info("QQQ \t holding ID is ${cert.holdingIdentity}")
            cert.tlsCertificates.forEach { pem ->
                logger.info("QQQ \t certificate is:")
                pem.lines().forEach { ln ->
                    logger.info("QQQ \t\t $ln")
                }
            }
        }
    }

    private inner class Processor : CompactedProcessor<String, GatewayTlsCertificates> {
        override val keyClass = String::class.java
        override val valueClass = GatewayTlsCertificates::class.java

        override fun onSnapshot(currentData: Map<String, GatewayTlsCertificates>) {
            aliasToCertificates.putAll(
                currentData.mapValues { entry ->
                    logMe(entry.key, entry.value)
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
            logMe(newRecord.key, newRecord.value)
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
