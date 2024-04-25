package net.corda.sdk.bootstrap.dbconfig.initial

import net.corda.sdk.bootstrap.initial.CryptoConfigParameters
import net.corda.sdk.bootstrap.initial.SecretsServiceType
import net.corda.sdk.bootstrap.initial.createDefaultCryptoConfigEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class TestCryptoConfigCreator {

    @Test
    fun `When Secrets Service type is Corda, should error when numberOfUnmanagedWrappingKeys is 0`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 0,
            type = SecretsServiceType.CORDA,
            passphrase = "some value",
            salt = "123",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = ".",
            vaultWrappingKeyPassphrases = listOf("some value"),
            vaultWrappingKeySalts = listOf("some value")
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `When Secrets Service type is Corda, should error when passphrase is null`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.CORDA,
            passphrase = null,
            salt = "123",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = ".",
            vaultWrappingKeyPassphrases = listOf("some value"),
            vaultWrappingKeySalts = listOf("some value")
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `When Secrets Service type is Corda, should error when salt is null`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.CORDA,
            passphrase = "some value",
            salt = null,
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = ".",
            vaultWrappingKeyPassphrases = listOf("some value"),
            vaultWrappingKeySalts = listOf("some value")
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultPath is null`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.VAULT,
            passphrase = "some value",
            salt = "123",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = null,
            vaultWrappingKeyPassphrases = listOf("some value"),
            vaultWrappingKeySalts = listOf("some value")
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultWrappingKeyPassphrases is empty`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.VAULT,
            passphrase = "some value",
            salt = "123",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = ".",
            vaultWrappingKeyPassphrases = emptyList(),
            vaultWrappingKeySalts = listOf("some value")
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultWrappingKeySalts is empty`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.VAULT,
            passphrase = "some value",
            salt = "123",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = ".",
            vaultWrappingKeyPassphrases = listOf("some value"),
            vaultWrappingKeySalts = emptyList()
        )
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters)
        }
    }

    @Test
    fun `Happy path for secret service Corda`() {
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.CORDA,
            passphrase = "some passphrase",
            salt = "some salt",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = null,
            vaultWrappingKeyPassphrases = emptyList(),
            vaultWrappingKeySalts = emptyList()
        )
        val conf = createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters).config
        assertThat(conf).contains("encryptedSecret")
    }

    @Test
    fun `Happy path for secret service Vault`() {
        val expectedVaultPath = "."
        val expectedWrappingKeyPassphrase = "some passphrase"
        val expectedWrappingKeySalt = "some salt"
        val cryptoConfigParameters = CryptoConfigParameters(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.VAULT,
            passphrase = null,
            salt = null,
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = expectedVaultPath,
            vaultWrappingKeyPassphrases = listOf(expectedWrappingKeyPassphrase),
            vaultWrappingKeySalts = listOf(expectedWrappingKeySalt)
        )
        val conf = createDefaultCryptoConfigEntity(cryptoConfigParameters = cryptoConfigParameters).config
        assertThat(conf)
            .contains(expectedVaultPath)
            .contains(expectedWrappingKeyPassphrase)
            .contains(expectedWrappingKeySalt)
    }
}
