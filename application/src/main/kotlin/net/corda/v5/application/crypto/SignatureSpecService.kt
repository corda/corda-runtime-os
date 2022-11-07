package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

@DoNotImplement
interface SignatureSpecService {
    /**
     * Works out a default signature spec for specified public key, given current security policies.
     *
     * @param publicKey the public key to be used for signing
     *
     * @return An appropriate [SignatureSpec], or null if nothing is available for the key type.
     */
    @Suspendable
    fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec?

    /**
     * Works out a default signature spec for specified public key and digest algorithm given current security policies.
     *
     * @param publicKey the public key to be used for signing
     * @param digestAlgorithmName the digest algorithm to use, e.g. [DigestAlgorithmName.SHA2_256]
     *
     * @return An appropriate [SignatureSpec], or null if nothing is available for the key type
     */
    @Suspendable
    fun defaultSignatureSpec(publicKey: PublicKey, digestAlgorithmName: DigestAlgorithmName): SignatureSpec?

    /**
     * Returns compatible signature specs for specified public key, given current security policies.
     *
     * @param publicKey the public key to be used for signing
     */
    @Suspendable
    fun compatibleSignatureSpecs(publicKey: PublicKey): List<SignatureSpec>

    /**
     * Returns compatible signature specs for specified public key and digest algorithm, given current security policies.
     *
     * @param publicKey the public key to be used for signing
     * @param digestAlgorithmName the digest algorithm to use, e.g. [DigestAlgorithmName.SHA2_256]
     */
    @Suspendable
    fun compatibleSignatureSpecs(publicKey: PublicKey, digestAlgorithmName: DigestAlgorithmName): List<SignatureSpec>
}