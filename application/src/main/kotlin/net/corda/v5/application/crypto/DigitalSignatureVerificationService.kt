package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import java.security.PublicKey

/**
 * Allows flows to verify digital signatures.
 *
 * Corda provides an instance of [DigitalSignatureVerificationService] to flows via property injection.
 */
@DoNotImplement
interface DigitalSignatureVerificationService {
    // TODO The following `verify` overload should be aligned with the other one as per: https://r3-cev.atlassian.net/browse/CORE-9332
    /**
     * Verifies a digital signature by using [signatureSpec].
     * Always throws an exception if verification fails.
     *
     * @param publicKey The signer's [PublicKey].
     * @param signatureSpec The signature spec.
     * @param signatureData The signatureData on a message.
     * @param clearData The clear data/message that was signed (usually the Merkle root).
     *
     * @throws CryptoSignatureException If verification of the digital signature fails.
     * @throws IllegalArgumentException If the signature scheme is not supported or if any of the clear or signature
     * data is empty.
     */
    fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Verifies a digital signature against data. Throws [CryptoSignatureException] if verification fails.
     *
     * @param originalData The original data on which the signature was applied (usually the Merkle root).
     * @param signature The digital signature.
     * @param publicKey The signer's [PublicKey].
     * @param signatureSpec The signature spec.
     *
     * @throws CryptoSignatureException If verification of the digital signature fails.
     * @throws IllegalArgumentException If the signature scheme is not supported or if any of the clear or signature
     * data is empty.
     */
    fun verify(originalData: ByteArray, signature: DigitalSignature, publicKey: PublicKey, signatureSpec: SignatureSpec)
}
