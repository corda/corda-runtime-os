package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Signature verification operations.
 */
interface SignatureVerificationService {
    /**
     * Verifies a digital signature by using [signatureSpec]. Always throws an exception if verification fails.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     *
     * @throws net.corda.v5.crypto.exceptions.CryptoSignatureException if verification fails.
     * @throws IllegalArgumentException if any of the clear or signature data is empty, key is invalid,
     * the signature scheme is not supported or in general arguments are wrong
     */
    fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Verifies a digital signature by inferring [SignatureSpec] from the [PublicKey] and [DigestAlgorithmName].
     * Always throws an exception if verification fails.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param digest is used together with the [PublicKey] to infer the [SignatureSpec] to use when verifying this signature.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     *
     * @throws net.corda.v5.crypto.exceptions.CryptoSignatureException if verification fails.
     * @throws IllegalArgumentException if any of the clear or signature data is empty, key is invalid,
     * the signature scheme is not supported, the [SignatureSpec] cannot
     * be inferred from the parameters - e.g. EdDSA supports only 'NONEwithEdDSA' signatures so if the SHA-256 will
     * be passed as the parameter that will result in the exception or in general arguments are wrong.
     */
    fun verify(publicKey: PublicKey, digest: DigestAlgorithmName, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Verifies a digital signature by using [signatureSpec].
     * It returns true if it succeeds and false if not. Normally you should use the function which throws an exception,
     * as it avoids the risk of failing to test the result.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed
     *
     * @return true if verification passes or false if verification fails.
     *
     * @throws IllegalArgumentException if any of the clear or signature data is empty, key is invalid,
     * the signature scheme is not supported or in general arguments are wrong
     */
    fun isValid(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray): Boolean

    /**
     * Verifies a digital signature by inferring [SignatureSpec] from the [PublicKey] and [DigestAlgorithmName].
     * It returns true if it succeeds and false if not. Normally you should use the function which throws an exception,
     * as it avoids the risk of failing to test the result.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param digest is used together with the [PublicKey] to infer the [SignatureSpec] to use when verifying this signature.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     *
     * @return true if verification passes or false if verification fails.
     *
     * @throws IllegalArgumentException if any of the clear or signature data is empty, key is invalid,
     * the signature scheme is not supported, the [SignatureSpec] cannot
     * be inferred from the parameters - e.g. EdDSA supports only 'NONEwithEdDSA' signatures so if the SHA-256 will
     * be passed as the parameter that will result in the exception or in general arguments are wrong.
     */
    fun isValid(publicKey: PublicKey, digest: DigestAlgorithmName, signatureData: ByteArray, clearData: ByteArray): Boolean
}