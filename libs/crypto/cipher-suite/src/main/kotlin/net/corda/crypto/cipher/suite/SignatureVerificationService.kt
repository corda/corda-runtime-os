package net.corda.crypto.cipher.suite

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
     * @param originalData the original data/message that was signed (usually the Merkle root).
     * @param signatureSpec the signature spec.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     *
     * @throws net.corda.v5.crypto.exceptions.CryptoSignatureException if verification fails.
     * @throws IllegalArgumentException if any of the original or signature data is empty, key is invalid,
     * the signature scheme is not supported or in general arguments are wrong
     */
    fun verify(originalData: ByteArray, signatureData: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec)

    /**
     * Verifies a digital signature by inferring [SignatureSpec] from the [PublicKey] and [DigestAlgorithmName].
     * Always throws an exception if verification fails.
     *
     * @param originalData the original data/message that was signed (usually the Merkle root).
     * @param signatureData the signatureData on a message.
     * @param publicKey the signer's [PublicKey].
     * @param digest is used together with the [PublicKey] to infer the [SignatureSpec] to use when verifying this signature.
     *
     * @throws net.corda.v5.crypto.exceptions.CryptoSignatureException if verification fails.
     * @throws IllegalArgumentException if any of the original or signature data is empty, key is invalid,
     * the signature scheme is not supported, the [SignatureSpec] cannot
     * be inferred from the parameters - e.g. EdDSA supports only 'NONEwithEdDSA' signatures so if the SHA-256 will
     * be passed as the parameter that will result in the exception or in general arguments are wrong.
     */
    fun verify(originalData: ByteArray, signatureData: ByteArray, publicKey: PublicKey, digest: DigestAlgorithmName)

    /**
     * Verifies a digital signature by using [signatureSpec].
     * It returns true if it succeeds and false if not. Normally you should use the function which throws an exception,
     * as it avoids the risk of failing to test the result.
     *
     * @param originalData the original data/message that was signed.
     * @param signatureData the signatureData on a message.
     * @param publicKey the signer's [PublicKey].
     * @param signatureSpec the signature spec.
     *
     * @return true if verification passes or false if verification fails.
     *
     * @throws IllegalArgumentException if any of the original or signature data is empty, key is invalid,
     * the signature scheme is not supported or in general arguments are wrong
     */
    fun isValid(originalData: ByteArray, signatureData: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): Boolean

    /**
     * Verifies a digital signature by inferring [SignatureSpec] from the [PublicKey] and [DigestAlgorithmName].
     * It returns true if it succeeds and false if not. Normally you should use the function which throws an exception,
     * as it avoids the risk of failing to test the result.
     *
     * @param originalData the original data/message that was signed (usually the Merkle root).
     * @param signatureData the signatureData on a message.
     * @param publicKey the signer's [PublicKey].
     * @param digest is used together with the [PublicKey] to infer the [SignatureSpec] to use when verifying this signature.
     *
     * @return true if verification passes or false if verification fails.
     *
     * @throws IllegalArgumentException if any of the original or signature data is empty, key is invalid,
     * the signature scheme is not supported, the [SignatureSpec] cannot
     * be inferred from the parameters - e.g. EdDSA supports only 'NONEwithEdDSA' signatures so if the SHA-256 will
     * be passed as the parameter that will result in the exception or in general arguments are wrong.
     */
    fun isValid(originalData: ByteArray, signatureData: ByteArray, publicKey: PublicKey, digest: DigestAlgorithmName): Boolean
}