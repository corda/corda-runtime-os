package net.corda.permissions.password.impl

import net.corda.crypto.core.Encryptor.Companion.encodePassPhrase
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import org.apache.commons.text.RandomStringGenerator
import java.security.SecureRandom
import java.util.*
import kotlin.math.abs

class PasswordServiceImpl(
    private val secureRandom: SecureRandom
) : PasswordService {

    companion object {
        private const val SALT_LENGTH = 100
    }

    private val saltGenerator = RandomStringGenerator.Builder()
        .withinRange("az".toCharArray(), "AZ".toCharArray(), "09".toCharArray())
        .usingRandom { maxCount ->
            abs(secureRandom.nextInt()) % maxCount
        }
        .build()

    override fun saltAndHash(clearTextPassword: String): PasswordHash {
        val salt = saltGenerator.generate(SALT_LENGTH)
        return doSaltAndHash(clearTextPassword, salt)
    }

    private fun doSaltAndHash(clearTextPassword: String, salt: String): PasswordHash {
        val encodedBytes = encodePassPhrase(clearTextPassword, salt)
        return PasswordHash(salt, Base64.getEncoder().encodeToString(encodedBytes))
    }

    override fun verifies(clearTextPassword: String, expected: PasswordHash): Boolean {
        val actual = doSaltAndHash(clearTextPassword, expected.salt)
        return actual == expected
    }
}