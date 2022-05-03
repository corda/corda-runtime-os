package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/**
 * Handles hashing of bytes.
 *
 * Delegates all functionality to [DigestService].
 */
@DoNotImplement
interface HashingService {

    /**
     * Default [DigestAlgorithmName] for this hashing service.
     */
    val defaultDigestAlgorithmName: DigestAlgorithmName

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     */
    @Suspendable
    fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Computes the digest of the [InputStream].
     *
     * @param inputStream The [InputStream] to hash.
     * @param digestAlgorithmName The digest algorithm to be used for hashing.
     */
    @Suspendable
    fun hash(inputStream : InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash

    /**
     * Returns the [DigestAlgorithmName] digest length in bytes.
     *
     * @param digestAlgorithmName The digest algorithm to get the digest length for.
     */
    @Suspendable
    fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int
}