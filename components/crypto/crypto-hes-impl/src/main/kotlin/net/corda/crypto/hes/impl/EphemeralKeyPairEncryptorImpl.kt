package net.corda.crypto.hes.impl

import net.corda.crypto.hes.HybridEncryptionParamsProvider
import net.corda.crypto.hes.EncryptedDataWithKey
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.crypto.hes.core.impl.encryptWithEphemeralKeyPair
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
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
        params: HybridEncryptionParamsProvider
    ): EncryptedDataWithKey =
        encryptWithEphemeralKeyPair(schemeMetadata, otherPublicKey, plainText, params)
}