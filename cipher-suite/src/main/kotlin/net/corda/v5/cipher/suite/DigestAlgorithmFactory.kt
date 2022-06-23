package net.corda.v5.cipher.suite

/**
 * A factory for custom digest implementations bypassing JCA.
 */
interface DigestAlgorithmFactory {
    /**
     * The algorithm name.
     */
    val algorithm: String

    /**
     * The factory method.
     */
    fun getInstance(): DigestAlgorithm
}