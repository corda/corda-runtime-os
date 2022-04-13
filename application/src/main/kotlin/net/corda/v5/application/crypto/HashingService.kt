package net.corda.v5.application.crypto

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash

/**
 * Handles hashing of bytes.
 *
 * Delegates all functionality to [DigestService].
 */
@DoNotImplement
interface
HashingService : DigestService, CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Default [DigestAlgorithmName] for this hashing service.
     */
    val defaultDigestAlgorithmName: DigestAlgorithmName

    /**
     * Computes the digest of the [ByteArray] using the default digest algorithm ([DigestAlgorithmName.DEFAULT_ALGORITHM_NAME]).
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun hash(bytes: ByteArray): SecureHash

    /**
     * Computes the digest of the [OpaqueBytes].
     *
     * @param opaqueBytes The [OpaqueBytes] to hash.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     */
    fun hash(opaqueBytes: OpaqueBytes, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Computes the digest of the [OpaqueBytes] using the default digest algorithm ([DigestAlgorithmName.DEFAULT_ALGORITHM_NAME]).
     *
     * @param opaqueBytes The [OpaqueBytes] to hash.
     */
    fun hash(opaqueBytes: OpaqueBytes): SecureHash

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str The [String] whose UTF-8 contents will be hashed.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     */
    fun hash(str: String, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents using the default digest algorithm ([DigestAlgorithmName.DEFAULT_ALGORITHM_NAME]).
     *
     * @param str The [String] whose UTF-8 contents will be hashed.
     */
    fun hash(str: String): SecureHash

    /**
     * Re-hashes [secureHash] bytes using its original digest algorithm.
     *
     * @param secureHash The [SecureHash] to re-hash.
     */
    fun reHash(secureHash: SecureHash): SecureHash

    /**
     * Generates a random hash value.
     */
    fun randomHash(digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Appends [second] digest to [first], and then computes the digest of the result. Both digests need to be of the same digest algorithm.
     * The result digest is of the same digest algorithm as well.
     *
     * @param first The first digest in the concatenation.
     * @param second The second digest in the concatenation.
     */
    fun concatenate(first: SecureHash, second: SecureHash): SecureHash
}