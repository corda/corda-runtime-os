package net.corda.crypto.cipher.suite

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
    fun hash(inputStream: InputStream, platformDigestName: DigestAlgorithmName): SecureHash

    /**
     * Parses a secure hash in string form into a [SecureHash].
     *
     * A valid secure hash string should be containing the algorithm and hexadecimal representation of the bytes
     * separated by the colon character (':') ([net.corda.v5.crypto.SecureHash.DELIMITER]).
     *
     * @param algoNameAndHexString The algorithm name followed by the hex string form of the digest,
     *                             separated by colon (':')
     *                             e.g. SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90.
     *
     * @throws IllegalArgumentException if the digest algorithm is not supported or if the hex string length does not
     *                                  meet the algorithm's digest length.
     */
    fun parseSecureHash(algoNameAndHexString: String): SecureHash

    /**
     * Returns the [DigestAlgorithmName] digest length in bytes.
     *
     * @param platformDigestName The digest algorithm to get the digest length for, looked up in platform supported
     * digest algorithms.
     *
     * @throws [IllegalArgumentException] if the digest algorithm is not supported.
     */
    fun digestLength(platformDigestName: DigestAlgorithmName): Int

    /**
     * Returns the defaulted digest algorithm.
     */
    fun defaultDigestAlgorithm(): DigestAlgorithmName

    /**
     * Returns the supported digest algorithms.
     */
    fun supportedDigestAlgorithms(): Set<DigestAlgorithmName>
}
