package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SignatureSpec
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * [DigitalSignatureVerificationService] allows flows to verify digital signatures.
 *
 * The platform will provide an instance of [DigitalSignatureVerificationService] to flows via property injection.
 */
@DoNotImplement
interface DigitalSignatureVerificationService {
    /**
     * Verifies a digital signature by using [signatureSpec].
     * Always throws an exception if verification fails.
     *
     * @param publicKey The signer's [PublicKey].
     * @param signatureData The signatureData on a message.
     * @param signatureSpec The signature spec.
     * @param clearData The clear data/message that was signed (usually the Merkle root).
     *
     * @throws InvalidKeyException If the key is invalid.
     * @throws SignatureException If verification fails.
     * @throws IllegalArgumentException If the signature scheme is not supported or if any of the clear or signature
     * data is empty.
     */
    fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray)
}
