package net.corda.crypto.ecies.impl

import net.corda.crypto.ecies.EncryptedDataWithKey
import net.corda.crypto.ecies.EphemeralKeyPairEncryptor
import net.corda.crypto.ecies.core.impl.encryptWithEphemeralKeyPair
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [EphemeralKeyPairEncryptor::class])
class EphemeralKeyPairEncryptorImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : EphemeralKeyPairEncryptor {
    override fun encrypt(
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?,
        salt: (PublicKey, PublicKey)  -> ByteArray
    ): EncryptedDataWithKey =
        encryptWithEphemeralKeyPair(schemeMetadata, otherPublicKey, plainText, aad, salt)
}