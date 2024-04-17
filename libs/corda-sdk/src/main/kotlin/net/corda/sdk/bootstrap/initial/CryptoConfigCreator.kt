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
import java.util.Base64

enum class SecretsServiceType {
    CORDA, VAULT
}

/**
 * Create the initial crypto configuration entity
 */
@Suppress("LongParameterList")
fun createDefaultCryptoConfigEntity(
    numberOfUnmanagedWrappingKeys: Int,
    type: SecretsServiceType,
    passphrase: String?,
    salt: String?,
    softHsmRootPassphrase: List<String>,
    softHsmRootSalt: List<String>,
    vaultPath: String?,
    vaultWrappingKeySalts: List<String>,
    vaultWrappingKeyPassphrases: List<String>
): ConfigEntity {
    require(numberOfUnmanagedWrappingKeys >= 1)
    val wrappingKeys = (1..numberOfUnmanagedWrappingKeys).toList().map { index ->
        val (wrappingPassphraseSecret, wrappingSaltSecret) = when (type) {
            SecretsServiceType.CORDA -> {
                requireNotNull(passphrase)
                requireNotNull(salt)
                createWrappingPassphraseForCordaSecretService(
                    index = index,
                    passphrase = passphrase,
                    salt = salt,
                    softHsmRootPassphrase = softHsmRootPassphrase,
                    softHsmRootSalt = softHsmRootSalt
                )
            }
            SecretsServiceType.VAULT -> {
                requireNotNull(vaultPath)
                require(vaultWrappingKeyPassphrases.size >= numberOfUnmanagedWrappingKeys)
                require(vaultWrappingKeySalts.size >= numberOfUnmanagedWrappingKeys)
                createWrappingPassphraseForVaultSecretService(
                    index = index,
                    vaultPath = vaultPath,
                    vaultWrappingKeyPassphrases = vaultWrappingKeyPassphrases,
                    vaultWrappingKeySalts = vaultWrappingKeySalts
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
