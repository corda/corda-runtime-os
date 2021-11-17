package net.corda.crypto

import org.slf4j.LoggerFactory
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordEncodeUtils {

    private val logger = LoggerFactory.getLogger(PasswordEncodeUtils::class.java)

    private const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val DERIVE_ITERATION_COUNT = 65536
    const val AES_KEY_LENGTH = 256

    fun encodePassPhrase(
        originalPassphrase: String?,
        originalSalt: String?,
        iterCount: Int = DERIVE_ITERATION_COUNT
    ): ByteArray {
        val passphrase = if (originalPassphrase.isNullOrBlank()) {
            logger.warn("Please specify the passphrase in the configuration, for now will be using the dev value!")
            "PASSPHRASE"
        } else {
            originalPassphrase
        }
        val salt = if (originalSalt.isNullOrBlank()) {
            logger.warn("Please specify the salt in the configuration, for now will be using the dev value!")
            "SALT"
        } else {
            originalSalt
        }
        /* Derive the key, given password and salt. */
        val factory = SecretKeyFactory.getInstance(DERIVE_ALGORITHM)
        val spec: KeySpec =
            PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, AES_KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return tmp.encoded
    }
}