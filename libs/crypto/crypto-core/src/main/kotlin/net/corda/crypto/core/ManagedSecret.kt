package net.corda.crypto.core

import net.corda.crypto.core.aes.AES_DERIVE_ITERATION_COUNT
import net.corda.crypto.core.aes.AES_PROVIDER
import net.corda.crypto.core.aes.secureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Supports HMAC calculation.
 */
class ManagedSecret(
    internal val secret: ByteArray
) {
    companion object {
        const val SECRET_DEFAULT_LENGTH = 32 // in bytes
        const val SECRET_MINIMAL_LENGTH = 32 // in bytes
        const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"

        /**
         * Creates an instance of [ManagedSecret] by generating a new AES key.
         */
        fun generate(size: Int = SECRET_DEFAULT_LENGTH): ManagedSecret =
            ManagedSecret(ByteArray(size).apply { secureRandom.nextBytes(this) })

        /**
         * Derives the secret from the passphrase and salt using PBKDF2WithHmacSHA256 algorithm.
         */
        fun derive(
            passphrase: String,
            salt: String,
            size: Int = SECRET_DEFAULT_LENGTH,
            iterCount: Int = AES_DERIVE_ITERATION_COUNT
        ): ManagedSecret {
            require(passphrase.isNotBlank()) {
                "The passphrase must not be blank string."
            }
            require(salt.isNotBlank()) {
                "The salt must not be blank string."
            }
            val factory = SecretKeyFactory.getInstance(
                DERIVE_ALGORITHM,
                AES_PROVIDER
            )
            val spec = PBEKeySpec(
                passphrase.toCharArray(),
                salt.toByteArray(),
                iterCount,
                size * Byte.SIZE_BITS
            )
            val tmp = factory.generateSecret(spec)
            return ManagedSecret(tmp.encoded)
        }
    }

    init {
        require(secret.size >= SECRET_MINIMAL_LENGTH) {
            "The secret must be at least $SECRET_MINIMAL_LENGTH bytes length, provided ${secret.size}."
        }
    }

    override fun hashCode(): Int {
        return secret.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ManagedSecret
        return secret.contentEquals(other.secret)
    }
}