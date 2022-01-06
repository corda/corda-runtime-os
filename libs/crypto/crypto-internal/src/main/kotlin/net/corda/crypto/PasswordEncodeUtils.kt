package net.corda.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordEncodeUtils {

    private const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val DERIVE_ITERATION_COUNT = 65536
    const val AES_KEY_LENGTH = 256
    const val AES_PROVIDER = "SunJCE"

    fun encodePassPhrase(passphrase: String, salt: String, iterCount: Int = DERIVE_ITERATION_COUNT): ByteArray {
        /* Derive the key, given password and salt. */
        val factory = SecretKeyFactory.getInstance(DERIVE_ALGORITHM, AES_PROVIDER)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, AES_KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return tmp.encoded
    }
}