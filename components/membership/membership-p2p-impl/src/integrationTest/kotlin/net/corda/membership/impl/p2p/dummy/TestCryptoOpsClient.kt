package net.corda.membership.impl.p2p.dummy

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.ShortHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PublicKey

interface TestCryptoOpsClient : CryptoOpsClient

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CryptoOpsClient::class, TestCryptoOpsClient::class])
internal class TestCryptoOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
) : TestCryptoOpsClient {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<CryptoOpsClient>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingFullIds: Boolean
    ): Collection<PublicKey> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        val keyScheme = schemeMetadata.findKeyScheme(scheme)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            keyScheme.algorithmName,
            schemeMetadata.providers.getValue(keyScheme.providerName)
        )
        keyPairGenerator.initialize(keyScheme.algSpec, schemeMetadata.secureRandom)
        return keyPairGenerator.generateKeyPair().public
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun freshKey(tenantId: String, category: String, scheme: String, context: Map<String, String>): PublicKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookupKeysByShortIds(tenantId: String, shortKeyIds: List<ShortHash>): List<CryptoSigningKey> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookupKeysByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): List<CryptoSigningKey> {
        throw UnsupportedOperationException()
    }

    override fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        return "secret".toByteArray()
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("TestCryptoOpsClient starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("TestCryptoOpsClient starting.")
        coordinator.stop()
    }
}