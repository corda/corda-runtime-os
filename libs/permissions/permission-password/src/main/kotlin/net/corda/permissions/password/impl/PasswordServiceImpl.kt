package net.corda.permissions.password.impl

import net.corda.crypto.impl.persistence.WrappingKey
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.apache.commons.text.RandomStringGenerator
import java.util.*
import kotlin.math.abs

class PasswordServiceImpl(private val cipherSchemeMetadata: CipherSchemeMetadata) : PasswordService {

    companion object {
        private const val SALT_LENGTH = 100
    }

    private val saltGenerator = RandomStringGenerator.Builder()
        .withinRange( "az".toCharArray(), "AZ".toCharArray(), "09".toCharArray())
        .usingRandom { maxCount ->
            abs(cipherSchemeMetadata.secureRandom.nextInt()) % maxCount
        }
        .build()

    override fun saltAndHash(clearTextPassword: String): PasswordHash {
        val salt = saltGenerator.generate(SALT_LENGTH)
        return doSaltAndHash(clearTextPassword, salt)
    }

    private fun doSaltAndHash(clearTextPassword: String, salt: String): PasswordHash {
        val wrappingKey = WrappingKey.deriveMasterKey(cipherSchemeMetadata, clearTextPassword, salt)
        val encodedBytes = wrappingKey.master.encoded
        return PasswordHash(salt, Base64.getEncoder().encodeToString(encodedBytes))
    }

    override fun verifies(clearTextPassword: String, expected: PasswordHash): Boolean {
        val actual = doSaltAndHash(clearTextPassword, expected.salt)
        return actual == expected
    }
}