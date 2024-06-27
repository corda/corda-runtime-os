@file:Suppress("MatchingDeclarationName")
// For the file name

package net.corda.sdk.bootstrap.initial

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.KeyDerivationParameters
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.helper.VaultSecretConfigGenerator
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.schema.configuration.ConfigKeys
import java.security.SecureRandom
import java.time.Instant
import java.util.*

enum class SecretsServiceType {
    CORDA, VAULT
}

data class CryptoConfigParameters(
    val numberOfUnmanagedWrappingKeys: Int,
    val type: SecretsServiceType,
    val passphrase: String?,
    val salt: String?,
    val softHsmRootPassphrase: List<String>,
    val softHsmRootSalt: List<String>,
    val vaultPath: String?,
    val vaultWrappingKeySalts: List<String>,
    val vaultWrappingKeyPassphrases: List<String>
)

/**
 * Create the initial crypto configuration entity
 */
fun createDefaultCryptoConfigEntity(
    cryptoConfigParameters: CryptoConfigParameters
): ConfigEntity {
    require(cryptoConfigParameters.numberOfUnmanagedWrappingKeys >= 1)
    val wrappingKeys = (1..cryptoConfigParameters.numberOfUnmanagedWrappingKeys).toList().map { index ->
        val (wrappingPassphraseSecret, wrappingSaltSecret) = when (cryptoConfigParameters.type) {
            SecretsServiceType.CORDA -> {
                requireNotNull(cryptoConfigParameters.passphrase)
                requireNotNull(cryptoConfigParameters.salt)
                createWrappingPassphraseForCordaSecretService(
                    index = index,
                    passphrase = cryptoConfigParameters.passphrase,
                    salt = cryptoConfigParameters.salt,
                    softHsmRootPassphrase = cryptoConfigParameters.softHsmRootPassphrase,
                    softHsmRootSalt = cryptoConfigParameters.softHsmRootSalt
                )
            }

            SecretsServiceType.VAULT -> {
                requireNotNull(cryptoConfigParameters.vaultPath)
                require(cryptoConfigParameters.vaultWrappingKeyPassphrases.size >= cryptoConfigParameters.numberOfUnmanagedWrappingKeys)
                require(cryptoConfigParameters.vaultWrappingKeySalts.size >= cryptoConfigParameters.numberOfUnmanagedWrappingKeys)
                createWrappingPassphraseForVaultSecretService(
                    index = index,
                    vaultPath = cryptoConfigParameters.vaultPath,
                    vaultWrappingKeyPassphrases = cryptoConfigParameters.vaultWrappingKeyPassphrases,
                    vaultWrappingKeySalts = cryptoConfigParameters.vaultWrappingKeySalts
                )
            }
        }

        KeyDerivationParameters(
            wrappingPassphraseSecret.root(),
            wrappingSaltSecret.root()
        )
    }

    val config = createDefaultCryptoConfig(wrappingKeys)
        .root()
        .render(ConfigRenderOptions.concise())

    val entity = ConfigEntity(
        section = ConfigKeys.CRYPTO_CONFIG,
        config = config,
        schemaVersionMajor = 1,
        schemaVersionMinor = 0,
        updateTimestamp = Instant.now(),
        updateActor = "init",
        isDeleted = false
    ).apply {
        version = 0
    }

    return entity
}

private fun createWrappingPassphraseForCordaSecretService(
    index: Int,
    passphrase: String,
    salt: String,
    softHsmRootPassphrase: List<String>,
    softHsmRootSalt: List<String>
): Pair<Config, Config> {
    val ess = EncryptionSecretsServiceImpl(
        passphrase,
        salt
    )
    val (passphraseSecretValue, saltSecretValue) = generateSecretValuesForType(index, softHsmRootPassphrase, softHsmRootSalt)
    return Pair(
        ess.createValue(passphraseSecretValue, "unused"),
        ess.createValue(saltSecretValue, "unused")
    )
}

private fun createWrappingPassphraseForVaultSecretService(
    index: Int,
    vaultPath: String,
    vaultWrappingKeyPassphrases: List<String>,
    vaultWrappingKeySalts: List<String>
): Pair<Config, Config> {
    val vss = VaultSecretConfigGenerator(vaultPath)
    return Pair(
        vss.createValue("unused", vaultWrappingKeyPassphrases[index - 1]),
        vss.createValue("unused", vaultWrappingKeySalts[index - 1])
    )
}

private fun generateSecretValuesForType(
    index: Int,
    softHsmRootPassphrase: List<String>,
    softHsmRootSalt: List<String>
): Pair<String, String> {
    val random = SecureRandom()

    // Use the salt and passphrase passed to the plugin by the user, or generate random ones if not supplied
    val wrappingPassphraseDefined = softHsmRootPassphrase.getOrNull(index - 1) ?: random.randomString()
    val wrappingSaltDefined = softHsmRootSalt.getOrNull(index - 1) ?: random.randomString()
    return Pair(wrappingPassphraseDefined, wrappingSaltDefined)
}

private fun SecureRandom.randomString(length: Int = 32): String = ByteArray(length).let {
    this.nextBytes(it)
    Base64.getEncoder().encodeToString(it)
}
