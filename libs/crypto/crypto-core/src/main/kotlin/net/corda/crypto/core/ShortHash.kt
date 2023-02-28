package net.corda.crypto.core

import net.corda.v5.crypto.SecureHash

/**
 * A short hash is simply the first 12 characters of a hex string.
 *
 * NOTE:  cannot be a data class at the moment, because a private constructor would be exposed by [copy()]]
 *
 * @throws [ShortHashException] if it cannot construct the short hash from the given constructor argument.
 */
class ShortHash private constructor(val value: String) {
    companion object {
        const val LENGTH = 12 // fixed "forever" - changing this will break everything

        private fun isHexString(hexString: String): Boolean =
            hexString.matches(Regex("[0-9a-fA-F]+"))

        /**
         * Creates a short hash from the given [hexString].
         *
         * For consistency with [SecureHash.toHexString], any lower case alpha characters are
         * converted to uppercase.
         *
         * @throws [ShortHashException] if the string is not hexadecimal, or short than [LENGTH].
         */
        fun of(hexString : String) : ShortHash {
            if (hexString.length < LENGTH) {
                throw ShortHashException("Hex string has length of ${hexString.length} but should be at least $LENGTH characters")
            }
            if (!isHexString(hexString)) {
                throw ShortHashException("Not a hex string: '$hexString'")
            }
            return ShortHash(hexString.substring(0, LENGTH).uppercase())
        }

        /**
         * Parses the given [hexString] and creates a short hash.
         *
         * For consistency with [SecureHash.toHexString], any lower case alpha characters are
         * converted to uppercase.
         *
         * @throws [ShortHashException] if the string is not hexadecimal or has length different from [LENGTH].
         */
        fun parse(hexString: String) : ShortHash {
            if (hexString.length != LENGTH) {
                throw ShortHashException("Hex string has length of ${hexString.length} " +
                        "but should be $LENGTH characters")
            }

            return of(hexString)
        }

        /**
         * Creates a short hash from the given secure hash.
         */
        fun of(secureHash: SecureHash) = of(secureHash.toHexString())
    }

    /**
     * Returns the value directly, because in many places we construct values in strings using
     *
     * ```
     *   val thing = "something_${holdingId.shortHash}"
     * ```
     */
    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShortHash

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
