package net.corda.crypto.ecdh.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.ecdh.StableKeyPairDecryptor
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [StableKeyPairDecryptor::class])
class StableKeyPairDecryptorImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
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
            digestName: String,
            salt: ByteArray,
            info: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray
    }

    override fun createActiveImpl(): Impl = ActiveImpl(cryptoOpsClient)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun decrypt(
        tenantId: String,
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray =
        impl.decrypt(tenantId, digestName, salt, info, publicKey, otherPublicKey, cipherText, aad)

    class InactiveImpl : Impl {
        override fun decrypt(
            tenantId: String,
            digestName: String,
            salt: ByteArray,
            info: ByteArray,
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
        private val cryptoOpsClient: CryptoOpsClient
    ) : Impl {
        override fun decrypt(
            tenantId: String,
            digestName: String,
            salt: ByteArray,
            info: ByteArray,
            publicKey: PublicKey,
            otherPublicKey: PublicKey,
            cipherText: ByteArray,
            aad: ByteArray?
        ): ByteArray =
            ECDHEncryptor.decrypt(
                digestName = digestName,
                salt = salt,
                info = info,
                publicKey = publicKey,
                otherPublicKey = otherPublicKey,
                cipherText = cipherText,
                aad = aad
            ) {
                cryptoOpsClient.deriveSharedSecret(tenantId, publicKey, otherPublicKey)
            }

        override fun close() = Unit
    }
}