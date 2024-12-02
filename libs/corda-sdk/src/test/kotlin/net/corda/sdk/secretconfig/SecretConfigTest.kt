package net.corda.sdk.secretconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretConfigTest {

    companion object {
        private const val SECRET = "secret"
        private const val SALT = "salt"
        private const val PASSPHRASE = "passphrase"
        private const val ENCODED_SECRET = "fi9drRPiKfLaIqZRuKIwbmuifE7DiNWLQxrNSNMg82yR8Q=="
    }

    @Test
    fun `test createCordaSecret`() {
        val result = SecretConfig.createCordaSecret(SECRET, PASSPHRASE, SALT)

        val expected = """(?s)\{"configSecret":\{"encryptedSecret":".*"}}"""
        assertTrue(expected.toRegex().matches(result))
    }

    @Test
    fun `test createVaultSecret`() {
        val vaultPath = "testVaultPath"
        val result = SecretConfig.createVaultSecret(SECRET, vaultPath)
        val expected = """(?s)\{"configSecret":\{"vaultKey":".*","vaultPath":"testVaultPath"}}"""
        assertTrue(expected.toRegex().matches(result))
    }

    @Test
    fun `test decrypt`() {
        val result = SecretConfig.decrypt(ENCODED_SECRET, PASSPHRASE, SALT)
        assertEquals(SECRET, result)
    }
}
