package net.corda.crypto.ecdh.impl

import net.corda.crypto.ecdh.EncryptedDataWithKey
import net.corda.crypto.ecdh.EphemeralKeyPairEncryptor
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.PublicKey

@Component(service = [EphemeralKeyPairEncryptor::class])
class EphemeralKeyPairEncryptorImpl(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : EphemeralKeyPairEncryptor {
    override fun encrypt(
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?
    ): EncryptedDataWithKey {
        val scheme = schemeMetadata.findKeyScheme(otherPublicKey)
        val provider = schemeMetadata.providers.getValue(scheme.providerName)
        val keyPair = generateEphemeralKeyPair(provider, scheme)
        val cipherText = ECDHEncryptor.encrypt(
            digestName = digestName,
            salt = salt,
            info = info,
            publicKey = keyPair.public,
            otherPublicKey = otherPublicKey,
            plainText = plainText,
            aad = aad
        ) {
            ECDHEncryptor.deriveSharedSecret(provider, keyPair.private, otherPublicKey)
        }
        return EncryptedDataWithKey(
            publicKey = keyPair.public,
            cipherText = cipherText
        )
    }

    private fun generateEphemeralKeyPair(
        provider: Provider,
        scheme: KeyScheme
    ): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            provider
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
        }
        return keyPairGenerator.generateKeyPair()
    }
}