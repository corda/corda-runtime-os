package net.corda.v5.crypto.extensions

import java.io.InputStream

/**
 * Digest extensions: Interface defining a custom digest calculation implementation. The interface should be implemented
 * if a CPK developer wishes to provide support for digest algorithms beyond those supported by the Corda platform.
 * The implementation of the interface must be coupled with the implementation of the [DigestAlgorithmFactory] which
 * will be used to create the instances. For each algorithm there must be matching a pair of
 * [DigestAlgorithmFactory] and [DigestAlgorithm] implementations.
 *
 *  Example of the algorithm and the factory implementations:
 *
 * ```kotlin
 * class TripleSha256Digest : DigestAlgorithm {
 *  companion object {
 *      const val ALGORITHM = "SHA-256-TRIPLE"
 *      const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
 *  }
 *  override val algorithm = ALGORITHM
 *  override val digestLength = 32
 *  override fun digest(bytes: ByteArray): ByteArray = bytes.sha256Bytes().sha256Bytes().sha256Bytes()
 *  override fun digest(inputStream: InputStream): ByteArray {
 *      val messageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
 *      val buffer = ByteArray(STREAM_BUFFER_SIZE)
 *      while (true) {
 *          val read = inputStream.read(buffer)
 *          if (read <= 0) break
 *          messageDigest.update(buffer, 0, read)
 *      }
 *      return messageDigest.digest().sha256Bytes().sha256Bytes()
 *  }
 * }
 *
 * class TripleSha256 : DigestAlgorithmFactory {
 *  override val algorithm: String = TripleSha256Digest.ALGORITHM
 *  override fun getInstance(): DigestAlgorithm = TripleSha256Digest()
 * }
 *```
 */
interface DigestAlgorithm {
    /**
     * Algorithm identifier, e.g. 'QUAD-SHA-256', the unique name (per Corda Platform and given CPK)
     * of the digest algorithm. The name must match the names used by the corresponding [DigestAlgorithmFactory].
     */
    val algorithm: String

    /**
     * The length of the digest in bytes.
     */
    val digestLength: Int

    /**
     * Computes the digest of the [ByteArray]. The computation must be stateless and thread safe.
     *
     * @param bytes The [ByteArray] to hash.
     * @return the hash value of the [digestLength]
     */
    fun digest(bytes: ByteArray): ByteArray

    /**
     * Computes the digest of the [InputStream] bytes. The computation must be stateless and thread safe.
     *
     * @param inputStream The [InputStream] to hash.
     * @return the hash value of the [digestLength]
     */
    fun digest(inputStream: InputStream): ByteArray
}
