package net.corda.crypto.ecies.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.ecies.StableKeyPairDecryptor
import net.corda.crypto.ecies.decryptWithStableKeyPair
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [StableKeyPairDecryptor::class])
class StableKeyPairDecryptorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : AbstractComponent<StableKeyPairDecryptorImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    )
), StableKeyPairDecryptor {
    interface Impl : AutoCloseable {
        fun decrypt(
            tenantId: String,
            salt: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray
    }

    override fun createActiveImpl(): Impl = ActiveImpl(schemeMetadata, cryptoOpsClient)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray =
        impl.decrypt(tenantId, salt, publicKey, otherPublicKey, cipherText, aad)

    class InactiveImpl : Impl {
        override fun decrypt(
            tenantId: String,
            salt: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray {
            throw IllegalStateException("The component is in invalid state.")
        }

        override fun close() = Unit
    }

    class ActiveImpl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val cryptoOpsClient: CryptoOpsClient
    ) : Impl {
        override fun decrypt(
            tenantId: String,
            salt: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray = decryptWithStableKeyPair(
            schemeMetadata = schemeMetadata,
            salt = salt,
            publicKey = publicKey,
            otherPublicKey = otherPublicKey,
            cipherText = cipherText,
            aad = aad
        ) { cryptoOpsClient.deriveSharedSecret(tenantId, it, otherPublicKey) }

        override fun close() = Unit
    }
}