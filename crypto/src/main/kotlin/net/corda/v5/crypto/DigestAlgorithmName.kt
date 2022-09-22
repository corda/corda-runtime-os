package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable

/**
 * The digest algorithm name. This class is to be used in Corda hashing API.
 *
 * @param name The name of the digest algorithm to be used for the instance.
 */
@CordaSerializable
class DigestAlgorithmName(
    /**
     * The name of the digest algorithm to be used for the instance.
     */
    val name: String
) {
    init {
        require(name.isNotBlank()) { "Hash algorithm name unavailable or not specified" }
    }

    companion object {
        /**
         * Instance of SHA-256
         */
        @JvmField
        val SHA2_256: DigestAlgorithmName = DigestAlgorithmName("SHA-256")

        /**
         * Instance of Double SHA-256
         */
        @JvmField
        val SHA2_256D: DigestAlgorithmName = DigestAlgorithmName("SHA-256D")

        /**
         * Instance of SHA-384
         */
        @JvmField
        val SHA2_384: DigestAlgorithmName = DigestAlgorithmName("SHA-384")

        /**
         * Instance of SHA-512
         */
        @JvmField
        val SHA2_512: DigestAlgorithmName = DigestAlgorithmName("SHA-512")

        /**
         * Instance of algorithm which is considered to be default. Set as SHA-256
         */
        @JvmField
        val DEFAULT_ALGORITHM_NAME: DigestAlgorithmName = SHA2_256
    }

    /**
     * Converts a [DigestAlgorithmName] object to a string representation.
     */
    override fun toString(): String = name

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int = name.uppercase().hashCode()

    /**
     * Returns a Boolean to indicate if the two specified instances of the [DigestAlgorithmName] are the same based on their content.
     */
    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if(this === other) return true
        val otherName = (other as? DigestAlgorithmName)?.name ?: return false
        return name.equals(otherName, true)
    }
}