package net.corda.crypto.hes.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.crypto.hes.core.impl.decryptWithStableKeyPair
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
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
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
        )
    )
), StableKeyPairDecryptor {

    override fun createActiveImpl(): Impl = Impl(schemeMetadata, cryptoOpsClient)

    override fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray =
        impl.decrypt(tenantId, salt, publicKey, otherPublicKey, cipherText, aad)

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val cryptoOpsClient: CryptoOpsClient
    ) : AbstractImpl {
        @Suppress("LongParameterList")
        fun decrypt(
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
    }
}