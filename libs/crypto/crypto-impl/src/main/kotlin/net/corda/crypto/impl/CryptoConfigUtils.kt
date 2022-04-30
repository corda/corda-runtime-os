package net.corda.crypto.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/*
{
  "rootKey": {
    "salt": "<plain-text-value>"
    "passphrase": {
        "configSecret": {
            "encryptedSecret": "<encrypted-value>"
        }
    },
    "softPersistence": {
        "expireAfterAccessMins": 60,
        "maximumSize": 100
    },
    "signingPersistence": {
        "expireAfterAccessMins": 60,
        "maximumSize": 100
    }
}
 */

private const val ROOT_KEY_PASSPHRASE = "rootKey.passphrase"
private const val ROOT_KEY_SALT = "rootKey.salt"
private const val SOFT_PERSISTENCE_OBJ = "softPersistence"
private const val SIGNING_PERSISTENCE_OBJ = "signingPersistence"

fun createDefaultCryptoConfig(smartFactoryKey: KeyCredentials, cryptoRootKey: KeyCredentials) =
    try {
        SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=${smartFactoryKey.passphrase}
            ${SmartConfigFactory.SECRET_SALT_KEY}=${smartFactoryKey.salt}
        """.trimIndent()
            )
        ).createDefaultCryptoConfig(cryptoRootKey)
    } catch (e: CryptoConfigurationException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoConfigurationException("Failed to create default crypto config", e)
    }

fun SmartConfigFactory.createDefaultCryptoConfig(cryptoRootKey: KeyCredentials): SmartConfig =
    try {
        this.create(
            ConfigFactory.empty()
                .withValue(ROOT_KEY_SALT, ConfigValueFactory.fromAnyRef(cryptoRootKey.salt))
                .withValue(
                    ROOT_KEY_PASSPHRASE, ConfigValueFactory.fromMap(
                        makeSecret(cryptoRootKey.passphrase).root().unwrapped()
                    )
                )
                .withValue(
                    SOFT_PERSISTENCE_OBJ, ConfigValueFactory.fromMap(
                        mapOf(
                            "expireAfterAccessMins" to "240",
                            "maximumSize" to "1000"
                        )
                    )
                )
                .withValue(
                    SIGNING_PERSISTENCE_OBJ, ConfigValueFactory.fromMap(
                        mapOf(
                            "expireAfterAccessMins" to "90",
                            "maximumSize" to "20"
                        )
                    )
                )
        )
    } catch (e: Throwable) {
        throw CryptoConfigurationException("Failed to create default crypto config", e)
    }

class CryptoPersistenceConfig(internal val config: SmartConfig) : SmartConfig by config {
    val expireAfterAccessMins: Long
        get() = try {
            config.getLong(this::expireAfterAccessMins.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::expireAfterAccessMins.name}", e)
        }

    val maximumSize: Long
        get() = try {
            config.getLong(this::maximumSize.name)
        } catch (e: Throwable) {
            throw CryptoConfigurationException("Failed to get ${this::maximumSize.name}", e)
        }
}

fun Map<String, SmartConfig>.toCryptoConfig(): SmartConfig =
    this[CRYPTO_CONFIG] ?: throw CryptoConfigurationException(
        "Could not generate a crypto configuration due to missing key: $CRYPTO_CONFIG"
    )

fun SmartConfig.rootEncryptor(): Encryptor =
    try {
        val key = AesKey.derive(
            passphrase = getString(ROOT_KEY_PASSPHRASE),
            salt = getString(ROOT_KEY_SALT)
        )
        AesEncryptor(key)
    } catch (e: Throwable) {
        throw CryptoConfigurationException("Failed to create Encryptor.", e)
    }

fun SmartConfig.softPersistence(): CryptoPersistenceConfig =
    try {
        CryptoPersistenceConfig(getConfig(SOFT_PERSISTENCE_OBJ))
    } catch (e: Throwable) {
        throw CryptoConfigurationException("Failed to create CryptoPersistenceConfig.", e)
    }

fun SmartConfig.signingPersistence(): CryptoPersistenceConfig =
    try {
        CryptoPersistenceConfig(getConfig(SIGNING_PERSISTENCE_OBJ))
    } catch (e: Throwable) {
        throw CryptoConfigurationException("Failed to create CryptoPersistenceConfig.", e)
    }
