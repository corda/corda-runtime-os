package net.corda.p2p.gateway

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.test.stub.crypto.processor.CouldNotFindPrivateKey
import net.corda.p2p.test.stub.crypto.processor.CouldNotReadKey
import net.corda.p2p.test.stub.crypto.processor.UnsupportedAlgorithm
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

class TestCryptoOpsClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
    keyStoreWithPasswordList: List<KeyStoreWithPassword>,
) : CryptoOpsClient {

    private val tenantIdToKeys = ConcurrentHashMap<String, TenantKeyMap>()
    init {
        keyStoreWithPasswordList.forEach { createTenantKeys(it) }
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoOpsClient>{ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
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

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
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

    private class TenantKeyMap {
        val publicKeyToPrivateKey = ConcurrentHashMap<PublicKey, PrivateKey>()
    }

    private fun toKeyPair(pem: String): KeyPair {
        return pem.reader().use {
            PEMParser(it).use { parser ->
                generateSequence {
                    parser.readObject()
                }.map {
                    if (it is PEMKeyPair) {
                        JcaPEMKeyConverter().getKeyPair(it)
                    } else {
                        null
                    }
                }.filterNotNull()
                    .firstOrNull()
            }
        } ?: throw CouldNotReadKey(pem)
    }

    fun createTenantKeys(keyStoreWithPassword: KeyStoreWithPassword) {
        val records = keyStoreWithPassword.keyStore.aliases().toList()
        for ((i, _) in records.withIndex()) {
            val tenantId = "tenantId"
            val privateKey = keyStoreWithPassword
                .keyStore
                .getKey(records[i], keyStoreWithPassword.password.toCharArray())
                .toPem()

            val tenantKeyMap = tenantIdToKeys.computeIfAbsent(tenantId) {
                TenantKeyMap()
            }
            val pair = toKeyPair(privateKey)
            tenantKeyMap.publicKeyToPrivateKey[pair.public] = pair.private
        }
    }

    fun removeTenantKeys() {
        tenantIdToKeys.clear()
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val privateKey = tenantIdToKeys[tenantId]
            ?.publicKeyToPrivateKey
            ?.get(publicKey)
            ?: throw CouldNotFindPrivateKey()
        val providerName = when (publicKey.algorithm) {
            "RSA" -> "SunRsaSign"
            "EC" -> "SunEC"
            else -> throw UnsupportedAlgorithm(publicKey)
        }
        val signature = Signature.getInstance(
            signatureSpec.signatureName,
            providerName
        )
        signature.initSign(privateKey)
        (signatureSpec as? ParameterizedSignatureSpec)?.let { signature.setParameter(it.params) }
        signature.update(data)
        return DigitalSignature.WithKey(publicKey, signature.sign(), context)
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

    override fun lookup(tenantId: String, ids: List<String>): List<CryptoSigningKey> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
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
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}
