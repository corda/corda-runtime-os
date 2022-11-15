package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/**
 * Digital digest calculation service.
 */
interface DigestService {

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Computes the digest of the [InputStream].
     *
     * @param inputStream The [InputStream] to hash.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun hash(inputStream : InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Returns the [DigestAlgorithmName] digest length in bytes.
     *
     * @param digestAlgorithmName The digest algorithm to get the digest length for.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int
}