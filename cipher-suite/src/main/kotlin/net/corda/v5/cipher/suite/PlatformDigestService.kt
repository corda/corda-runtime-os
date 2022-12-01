package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/**
 * The platform digest calculation service. Holds a list of supported digest algorithms to be used for hashing.
 */
interface PlatformDigestService {

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     * @param platformDigestName The digest algorithm to be used for hashing, looked up in platform supported digest algorithms.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun hash(bytes: ByteArray, platformDigestName: DigestAlgorithmName): SecureHash

    /**
     * Computes the digest of the [InputStream].
     *
     * @param inputStream The [InputStream] to hash.
     * @param platformDigestName The digest algorithm to be used for hashing, looked up in platform supported digest algorithms.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun hash(inputStream : InputStream, platformDigestName: DigestAlgorithmName): SecureHash

    /**
     * Returns the [DigestAlgorithmName] digest length in bytes.
     *
     * @param platformDigestName The digest algorithm to get the digest length for, looked up in platform supported
     * digest algorithms.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun digestLength(platformDigestName: DigestAlgorithmName): Int
}