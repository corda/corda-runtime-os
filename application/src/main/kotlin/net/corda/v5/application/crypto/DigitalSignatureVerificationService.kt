package net.corda.v5.application.crypto

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureVerificationService
import java.security.InvalidKeyException
import java.security.SignatureException

/**
 * The [DigitalSignatureVerificationService] digital signature verification operations.
 */
interface DigitalSignatureVerificationService : SignatureVerificationService, CordaServiceInjectable, CordaFlowInjectable {
    /**
     * Verifies a [DigitalSignatureAndMetadata].
     *
     * Always throws an exception if verification fails.
     *
     * @param hash the hash value that is signed.
     * @param signature the signature on the hash.
     *
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException  if verification fails.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verify(hash: SecureHash, signature: DigitalSignatureAndMetadata)

    /**
     * Verifies a [DigitalSignatureAndMetadata].
     *
     * Returns `true` if it succeeds and `false` if not. In comparison to [verify] if the key and signature does not match it returns
     * `false` rather than throwing an exception. Normally you should use the function which throws, as it avoids the risk of failing to
     * test the result.
     *
     * @param hash the hash value that is signed.
     * @param signature the signature on the hash.
     *
     * @return `true` if verification passes or `false` if verification fails.
     */
    fun isValid(hash: SecureHash, signature: DigitalSignatureAndMetadata): Boolean
}