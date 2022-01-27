package net.corda.permissions.password.impl

import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.permissions.password.PasswordServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordServiceImplTest {

    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val passwordService = PasswordServiceFactory().createPasswordService(cipherSchemeMetadata.secureRandom)

    @Test
    fun `password service salt and hash produces salt and hash with length less than 200`() {
        val correctPassword = "myPassword"

        val passwordHash = passwordService.saltAndHash(correctPassword)
        assertThat(passwordHash.salt.length).isLessThan(200)
        assertThat(passwordHash.value.length).isLessThan(200)
    }

    @Test
    fun `password service salt and hash verifies the correct password`() {
        val correctPassword = "myPassword"

        val passwordHash = passwordService.saltAndHash(correctPassword)

        assertTrue(passwordService.verifies(correctPassword, passwordHash))
        assertFalse(passwordService.verifies("completelyRandom", passwordHash))
    }

    @Test
    fun `password service salt and hash produces a different result every time with the same password`() {
        val correctPassword = "myPassword"

        val passwordHash = passwordService.saltAndHash(correctPassword)

        // Do second round of salt and hash on exactly the same password and check that
        // password hash and salt value are different
        with(passwordService.saltAndHash(correctPassword)) {
            assertThat(salt.length).isLessThan(200)
            assertThat(value.length).isLessThan(200)

            assertThat(salt).isNotEqualTo(passwordHash.salt)
            assertThat(value).isNotEqualTo(passwordHash.value)
        }
    }
}