package net.corda.membership.impl.registration.dummy

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

interface TestCryptoOpsClient : CryptoOpsClient

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CryptoOpsClient::class, TestCryptoOpsClient::class])
class TestCryptoOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
) : TestCryptoOpsClient {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
        private val keys: ConcurrentHashMap<String, CryptoSigningKey> = ConcurrentHashMap()
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

    override fun filterMyKeys(tenantId: String, candidateKeys: Collection<PublicKey>): Collection<PublicKey> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun filterMyKeysByFullIds(tenantId: String, candidateKeys: Collection<PublicKey>): Collection<PublicKey> {
        TODO("Not yet implemented")
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
        val publicKey = keyPairGenerator.generateKeyPair().public
        val keyId = publicKey.publicKeyId()
        keys[keyId] = CryptoSigningKey(
            keyId,
            tenantId,
            category,
            alias,
            alias,
            ByteBuffer.wrap(publicKey.encoded),
            scheme,
            alias,
            1,
            "1",
            UTCClock().instant()
        )
        return publicKey
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
    ) = DigitalSignature.WithKey(publicKey, byteArrayOf(1), emptyMap())

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

    override fun lookup(tenantId: String, ids: List<String>): List<CryptoSigningKey> {
        val result = mutableListOf<CryptoSigningKey>()
        ids.forEach {
            result.add(keys[it] ?: throw IllegalArgumentException("No key found under ID: $it."))
        }
        return result
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
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
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
