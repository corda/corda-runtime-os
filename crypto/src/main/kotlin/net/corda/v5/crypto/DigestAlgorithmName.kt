package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable

/**
 * The digest algorithm name. This class is to be used in Corda hashing API.
 *
 * @property name The name of the digest algorithm to be used for the instance.
 */
@CordaSerializable
class DigestAlgorithmName(val name: String) {
    init {
        require(name.isNotBlank()) { "Hash algorithm name unavailable or not specified" }
    }

    companion object {

        const val NONCE_SIZE = 8

        @JvmField
        val SHA2_256: DigestAlgorithmName = DigestAlgorithmName("SHA-256")

        @JvmField
        val SHA2_384: DigestAlgorithmName = DigestAlgorithmName("SHA-384")

        @JvmField
        val SHA2_512: DigestAlgorithmName = DigestAlgorithmName("SHA-512")

        /**
         * The [DEFAULT_ALGORITHM_NAME] instance will be parametrized and initialized at runtime.
         *
         * It would be probably useful to assume an override priority order.
         */
        @JvmField
        val DEFAULT_ALGORITHM_NAME: DigestAlgorithmName = SHA2_256
    }

    override fun toString(): String = name

    override fun hashCode(): Int = name.uppercase().hashCode()

    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if(this === other) return true
        val otherName = (other as? DigestAlgorithmName)?.name ?: return false
        return name.equals(otherName, true)
    }
}