package net.corda.v5.crypto.extensions

/**
 * Digest extensions: Interface defining a factory creating a custom digest implementation. The interface
 * should be implemented if a CPK developer wishes to provide support for digest algorithms beyond supported
 * by the Corda Platform.
 * For each algorithm there must be matching a pair of [DigestAlgorithmFactory] and [DigestAlgorithm] implementations.
 * @see DigestAlgorithm for an example on how to use it.
 */
interface DigestAlgorithmFactory {
    /**
     * The algorithm name, e.g. 'QUAD-SHA-256', the unique name (per Corda Platform and given CPK)
     * of the digest algorithm. The name must match the names used by the created [DigestAlgorithm].
     */
    val algorithm: String

    /**
     * The factory method. The method must return a new instance on each call. The method must be thread safe.
     */
    fun getInstance(): DigestAlgorithm
}