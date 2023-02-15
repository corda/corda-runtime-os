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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.ShortHash
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

internal class TestCryptoOpsClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
) : CryptoOpsClient {

    private val tenantIdToKeys = ConcurrentHashMap<String, MutableMap<PublicKey, PrivateKey>>()

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoOpsClient> { event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    private fun Key.publicKey(): PublicKey? =
        this.toPem()
            .reader()
            .use {
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
            }?.public

    fun createTenantKeys(keyStoreWithPassword: KeyStoreWithPassword, tenantId: String) {
        keyStoreWithPassword.keyStore.aliases().toList().forEach { alias ->
            val privateKey = keyStoreWithPassword
                .keyStore
                .getKey(
                    alias,
                    keyStoreWithPassword.password.toCharArray()
                ) as? PrivateKey ?:  throw CordaRuntimeException("Missing private key")
            val publicKey = privateKey.publicKey() ?: throw CordaRuntimeException("Can not read public key")

            tenantIdToKeys.computeIfAbsent(tenantId) {
                ConcurrentHashMap()
            }[publicKey] = privateKey
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
        context: Map<String, String>,
    ): DigitalSignature.WithKey {
        val privateKey = tenantIdToKeys[tenantId]
            ?.get(publicKey)
            ?: throw CordaRuntimeException("Could not find private key")
        val providerName = when (publicKey.algorithm) {
            "RSA" -> "SunRsaSign"
            "EC" -> "SunEC"
            else -> throw CordaRuntimeException("Unsupported Algorithm")
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
        context: Map<String, String>,
    ): DigitalSignature.WithKey {
        throw UnsupportedOperationException()
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> {
        throw UnsupportedOperationException()
    }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        throw UnsupportedOperationException()
    }

    override fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingFullIds: Boolean
    ): Collection<PublicKey> {
        throw UnsupportedOperationException()
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        throw UnsupportedOperationException()
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        throw UnsupportedOperationException()
    }

    override fun freshKey(tenantId: String, category: String, scheme: String, context: Map<String, String>): PublicKey {
        throw UnsupportedOperationException()
    }

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        throw UnsupportedOperationException()
    }

    override fun lookupKeysByShortIds(tenantId: String, shortKeyIds: List<ShortHash>): List<CryptoSigningKey> {
        throw UnsupportedOperationException()
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
        throw UnsupportedOperationException()
    }

    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        throw UnsupportedOperationException()
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
