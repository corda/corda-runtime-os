package net.corda.permissions.password.impl

import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.permissions.password.PasswordServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordServiceImplTest {

    private val cipherSchemeMetadataProvider = CipherSchemeMetadataProviderImpl()
    private val cipherSchemeMetadata = cipherSchemeMetadataProvider.getInstance()
    private val passwordService = PasswordServiceFactory().createPasswordService(cipherSchemeMetadata.secureRandom)

    @Test
    fun test() {
        val correctPassword = "myPassword"

        val passwordHash = passwordService.saltAndHash(correctPassword)
        assertThat(passwordHash.salt.length).isLessThan(200)
        assertThat(passwordHash.value.length).isLessThan(200)

        assertTrue(passwordService.verifies(correctPassword, passwordHash))
        assertFalse(passwordService.verifies("completelyRandom", passwordHash))
    }
}