package net.corda.sdk.bootstrap.dbconfig.initial

import net.corda.sdk.bootstrap.initial.SecretsServiceType
import net.corda.sdk.bootstrap.initial.createDefaultCryptoConfigEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class TestCryptoConfigCreator {

    @Test
    fun `When Secrets Service type is Corda, should error when numberOfUnmanagedWrappingKeys is 0`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `When Secrets Service type is Corda, should error when passphrase is null`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `When Secrets Service type is Corda, should error when salt is null`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultPath is null`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultWrappingKeyPassphrases is empty`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `When Secrets Service type is Vault, should error when vaultWrappingKeySalts is empty`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createDefaultCryptoConfigEntity(
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
        }
    }

    @Test
    fun `Happy path for secret service Corda`() {
        val conf = createDefaultCryptoConfigEntity(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.CORDA,
            passphrase = "some passphrase",
            salt = "some salt",
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = null,
            vaultWrappingKeyPassphrases = emptyList(),
            vaultWrappingKeySalts = emptyList()
        ).config
        assertThat(conf).contains("encryptedSecret")
    }

    @Test
    fun `Happy path for secret service Vault`() {
        val expectedVaultPath = "."
        val expectedWrappingKeyPassphrase = "some passphrase"
        val expectedWrappingKeySalt = "some salt"
        val conf = createDefaultCryptoConfigEntity(
            numberOfUnmanagedWrappingKeys = 1,
            type = SecretsServiceType.VAULT,
            passphrase = null,
            salt = null,
            softHsmRootPassphrase = emptyList(),
            softHsmRootSalt = emptyList(),
            vaultPath = expectedVaultPath,
            vaultWrappingKeyPassphrases = listOf(expectedWrappingKeyPassphrase),
            vaultWrappingKeySalts = listOf(expectedWrappingKeySalt)
        ).config
        assertThat(conf)
            .contains(expectedVaultPath)
            .contains(expectedWrappingKeyPassphrase)
            .contains(expectedWrappingKeySalt)
    }
}
