package net.corda.v5.application.crypto

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.crypto.SignatureSpec
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * The [DigitalSignatureVerificationService] digital signature verification operations.
 */
interface DigitalSignatureVerificationService : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Verifies a digital signature by using [signatureSpec].
     * Always throws an exception if verification fails.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException  if verification fails.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray)
}