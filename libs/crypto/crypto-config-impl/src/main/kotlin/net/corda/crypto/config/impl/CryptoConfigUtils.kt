package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.cipher.suite.ConfigurationSecrets
import net.corda.v5.crypto.failures.CryptoException
import java.util.UUID

/*
{
    "cryptoConnectionFactory": {
        "expireAfterAccessMins": 5,
        "maximumSize": 3
    },
    "signingService": {
        "cache": {
            "expireAfterAccessMins": 60,
            "maximumSize": 1000
        }
    },
    "hsmService": {
        "cache": {
            "expireAfterAccessMins": 5,
            "maximumSize": 10
        },
        "downstreamMaxAttempts": 3
    },
    "hsmId": "SOFT",
    "hsmMap": {
        "SOFT": {
            "workerSuffix": "",
            "retry" : {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "SOFT",
                "categories" : [
                    {
                        "category": "*",
                        "policy": "WRAPPED"
                    }
                ],
                "masterKeyPolicy": "UNIQUE",
                "capacity": "-1",
                "supportedSchemes": [
                    "CORDA.RSA",
                    "CORDA.ECDSA.SECP256R1",
                    "CORDA.ECDSA.SECP256K1",
                    "CORDA.EDDSA.ED25519",
                    "CORDA.X25519",
                    "CORDA.SM2",
                    "CORDA.GOST3410.GOST3411",
                    "CORDA.SPHINCS-256"
                ],
                "cfg": {
                    "keyMap": {
                        "name": "CACHING",
                        "cache": {
                            "expireAfterAccessMins": 60,
                            "maximumSize": 1000
                        }
                    },
                    "wrappingKeyMap": {
                        "name": "CACHING",
                        "salt": "<plain-text-value>",
                        "passphrase": {
                            "configSecret": {
                                "encryptedSecret": "<encrypted-value>"
                            }
                        },
                        "cache": {
                            "expireAfterAccessMins": 60,
                            "maximumSize": 100
                        }
                    },
                    "wrapping": {
                        "name": "DEFAULT",
                        "hsm": {
                            "name": "..",
                            "config": {
                            }
                        }
                    }
                }
            }
        },
        "AWS": {
            "retry" : {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "AWS",
                "categories" : [
                    {
                        "category": "ACCOUNTS",
                        "policy": "WRAPPED"
                    }, {
                        "category": "CI",
                        "policy": "WRAPPED"
                    }, {
                        "category": "*",
                        "policy": "ALIASED"
                    }
                ],
                "masterKeyPolicy": "SHARED",
                "masterKeyAlias": "cordawrappingkey",
                "capacity": "3300",
                "supportedSchemes": [
                    "CORDA.RSA",
                    "CORDA.ECDSA.SECP256R1",
                    "CORDA.ECDSA.SECP256K1",
                    "CORDA.X25519"
                ],
                "config": {
                    "username": "user",
                    "passphrase": {
                        "configSecret": {
                            "encryptedSecret": "<encrypted-value>"
                        }
                    },
                    "partition": "whatever"
                }
        }
    },
    "bus": {
        "processors": {
            "ops": {
                "maxAttempts": 3,
                "waitBetweenMills": [200]
            },
            "flow": {
                "maxAttempts": 3,
                "waitBetweenMills": [200]
            },
            "config": {
                "maxAttempts": 3,
                "waitBetweenMills": [200]
            },
            "registration": {
                "maxAttempts": 3,
                "waitBetweenMills": [200]
            }
        }
    }
}
 */

private const val CRYPTO_CONNECTION_FACTORY_OBJ = "cryptoConnectionFactory"
private const val SIGNING_SERVICE_OBJ = "signingService"
private const val HSM_SERVICE_OBJ = "hsmService"
private const val HSM_ID = "hsmId"
private const val HSM_MAP = "hsmMap"
private const val HSM_MAP_ITEM_OBJ = "hsmMap.%s"
private val DEFAULT_HSM_OBJ = String.format(HSM_MAP_ITEM_OBJ, SOFT_HSM_ID)
private val MASTER_WRAPPING_KEY_SALT = DEFAULT_HSM_OBJ +
        CryptoHSMConfig::hsm.name +
        CryptoHSMConfig.HSMConfig::cfg.name +
        "wrappingKeyMap.salt"
private val MASTER_WRAPPING_KEY_PASSPHRASE = DEFAULT_HSM_OBJ +
        CryptoHSMConfig::hsm.name +
        CryptoHSMConfig.HSMConfig::cfg.name +
        "wrappingKeyMap.passphrase"
private const val BUS_PROCESSORS_OBJ = "bus.processors"
private const val OPS_BUS_PROCESSOR_OBJ = "ops"
private const val FLOW_BUS_PROCESSOR_OBJ = "flow"
private const val HSM_CONFIG_BUS_PROCESSOR_OBJ = "config"
private const val HSM_REGISTRATION_BUS_PROCESSOR_OBJ = "registration"

fun Map<String, SmartConfig>.toCryptoConfig(): SmartConfig =
    this[CRYPTO_CONFIG] ?: throw IllegalStateException(
        "Could not generate a crypto configuration due to missing key: $CRYPTO_CONFIG"
    )

fun SmartConfig.toConfigurationSecrets(): ConfigurationSecrets = ConfigurationSecretsImpl(this)

fun SmartConfig.cryptoConnectionFactory(): CryptoConnectionsFactoryConfig =
    try {
        CryptoConnectionsFactoryConfig(getConfig(CRYPTO_CONNECTION_FACTORY_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $CRYPTO_CONNECTION_FACTORY_OBJ.", e)
    }

fun SmartConfig.signingService(): CryptoSigningServiceConfig =
    try {
        CryptoSigningServiceConfig(getConfig(SIGNING_SERVICE_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $SIGNING_SERVICE_OBJ.", e)
    }

fun SmartConfig.hsmId(): String =
    try {
        getString(HSM_ID)
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $HSM_ID.", e)
    }

fun SmartConfig.hsmMap(): Map<String, CryptoHSMConfig> =
    try {
        val set = getConfig(HSM_MAP)
        set.root().keys.associateWith {
            CryptoHSMConfig(set.getConfig(it))
        }
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $HSM_MAP.", e)
    }

fun SmartConfig.hsm(id: String): CryptoHSMConfig {
    val path = String.format(HSM_MAP_ITEM_OBJ, id)
    return try {
        CryptoHSMConfig(getConfig(path))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $path.", e)
    }
}

fun SmartConfig.hsm(): CryptoHSMConfig = hsm(hsmId())

fun SmartConfig.hsmService(): CryptoHSMServiceConfig =
    try {
        CryptoHSMServiceConfig(getConfig(HSM_SERVICE_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $HSM_SERVICE_OBJ.", e)
    }

fun SmartConfig.opsBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(OPS_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for ops operations.", e)
    }

fun SmartConfig.flowBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(FLOW_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for flow ops operations.", e)
    }

fun SmartConfig.hsmConfigBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(HSM_CONFIG_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for hsm config operations.", e)
    }

fun SmartConfig.hsmRegistrationBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(HSM_REGISTRATION_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for hsm registration operations.", e)
    }

fun createDefaultCryptoConfig(smartFactoryKey: KeyCredentials): SmartConfig =
    createDefaultCryptoConfig(
        smartFactoryKey = smartFactoryKey,
        masterWrappingKey = KeyCredentials(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        )
    )

fun createDefaultCryptoConfig(smartFactoryKey: KeyCredentials, masterWrappingKey: KeyCredentials): SmartConfig =
    SmartConfigFactory.create(
        ConfigFactory.parseString(
            """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=${smartFactoryKey.passphrase}
            ${SmartConfigFactory.SECRET_SALT_KEY}=${smartFactoryKey.salt}
        """.trimIndent()
        )
    ).createDefaultCryptoConfig(masterWrappingKey)

fun SmartConfig.addDefaultBootCryptoConfig(fallbackMasterWrappingKey: KeyCredentials): SmartConfig {
    val cryptoLibrary = if (hasPath(BOOT_CRYPTO)) {
        getConfig(BOOT_CRYPTO)
    } else {
        null
    }
    val masterWrappingKeySalt = if (cryptoLibrary?.hasPath(MASTER_WRAPPING_KEY_SALT) == true) {
        cryptoLibrary.getString(MASTER_WRAPPING_KEY_SALT)
    } else {
        fallbackMasterWrappingKey.salt
    }
    val masterWrappingKeyPassphrase =
        if (cryptoLibrary?.hasPath(MASTER_WRAPPING_KEY_PASSPHRASE) == true) {
            cryptoLibrary.getString(MASTER_WRAPPING_KEY_PASSPHRASE)
        } else {
            fallbackMasterWrappingKey.passphrase
        }
    val masterWrappingKey = KeyCredentials(masterWrappingKeyPassphrase, masterWrappingKeySalt)
    return withFallback(
        withValue(
            BOOT_CRYPTO,
            ConfigValueFactory.fromMap(
                factory.createDefaultCryptoConfig(masterWrappingKey).root().unwrapped()
            )
        )
    )
}

fun SmartConfigFactory.createDefaultCryptoConfig(masterWrappingKey: KeyCredentials): SmartConfig = try {
    this.create(
        ConfigFactory.empty()
            .withValue(
                CRYPTO_CONNECTION_FACTORY_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoConnectionsFactoryConfig::expireAfterAccessMins.name to "5",
                        CryptoConnectionsFactoryConfig::maximumSize.name to "3"
                    )
                )
            )
            .withValue(
                SIGNING_SERVICE_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoSigningServiceConfig::cache.name to mapOf(
                            CryptoSigningServiceConfig.CacheConfig::expireAfterAccessMins.name to "60",
                            CryptoSigningServiceConfig.CacheConfig::maximumSize.name to "1000"
                        )
                    )
                )
            )
            .withValue(
                HSM_SERVICE_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoHSMServiceConfig::cache.name to mapOf(
                            CryptoHSMServiceConfig.CacheConfig::expireAfterAccessMins.name to "5",
                            CryptoHSMServiceConfig.CacheConfig::maximumSize.name to "10"
                        ),
                        CryptoHSMServiceConfig::downstreamMaxAttempts.name to "3"
                    )
                )
            )
            .withValue(
                HSM_ID, ConfigValueFactory.fromAnyRef(SOFT_HSM_ID)
            )
            .withValue(
                DEFAULT_HSM_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoHSMConfig::workerSuffix.name to "",
                        CryptoHSMConfig::retry.name to mapOf(
                            CryptoHSMConfig.RetryConfig::maxAttempts.name to "3",
                            CryptoHSMConfig.RetryConfig::attemptTimeoutMills.name to "20000",
                        ),
                        CryptoHSMConfig::hsm.name to mapOf(
                            CryptoHSMConfig.HSMConfig::name.name to SOFT_HSM_SERVICE_NAME,
                            CryptoHSMConfig.HSMConfig::categories.name to listOf(
                                mapOf(
                                    CryptoHSMConfig.CategoryConfig::category.name to "*",
                                    CryptoHSMConfig.CategoryConfig::policy.name to PrivateKeyPolicy.WRAPPED.name,
                                )
                            ),
                            CryptoHSMConfig.HSMConfig::masterKeyPolicy.name to MasterKeyPolicy.UNIQUE.name,
                            CryptoHSMConfig.HSMConfig::capacity.name to "-1",
                            CryptoHSMConfig.HSMConfig::supportedSchemes.name to listOf(
                                "CORDA.RSA",
                                "CORDA.ECDSA.SECP256R1",
                                "CORDA.ECDSA.SECP256K1",
                                "CORDA.EDDSA.ED25519",
                                "CORDA.X25519",
                                "CORDA.SM2",
                                "CORDA.GOST3410.GOST3411",
                                "CORDA.SPHINCS-256"
                            ),
                            CryptoHSMConfig.HSMConfig::cfg.name to mapOf(
                                "keyMap" to mapOf(
                                    "name" to "CACHING",
                                    "cache" to mapOf(
                                        "expireAfterAccessMins" to "60",
                                        "maximumSize" to "1000"
                                    )
                                ),
                                "wrappingKeyMap" to mapOf(
                                    "name" to "CACHING",
                                    "salt" to masterWrappingKey.salt,
                                    "passphrase" to ConfigValueFactory.fromMap(
                                        makeSecret(masterWrappingKey.passphrase).root().unwrapped()
                                    ),
                                    "cache" to mapOf(
                                        "expireAfterAccessMins" to "60",
                                        "maximumSize" to "100"
                                    )
                                ),
                                "wrapping" to mapOf(
                                    "name" to "DEFAULT"
                                )
                            )
                        )
                    )
                )
            )
            .withValue(
                BUS_PROCESSORS_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        OPS_BUS_PROCESSOR_OBJ to mapOf(
                            CryptoBusProcessorConfig::maxAttempts.name to "3",
                            CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        FLOW_BUS_PROCESSOR_OBJ to mapOf(
                            CryptoBusProcessorConfig::maxAttempts.name to "3",
                            CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        HSM_CONFIG_BUS_PROCESSOR_OBJ to mapOf(
                            CryptoBusProcessorConfig::maxAttempts.name to "3",
                            CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        HSM_REGISTRATION_BUS_PROCESSOR_OBJ to mapOf(
                            CryptoBusProcessorConfig::maxAttempts.name to "3",
                            CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        )
                    ),
                )
            )
    )
} catch (e: Throwable) {
    throw CryptoException("Failed to create default crypto config", e)
}

private class ConfigurationSecretsImpl(
    private val owner: SmartConfig
) : ConfigurationSecrets {
    override fun getSecret(secret: Map<String, Any>): String =
        owner.convert(
            ConfigFactory.parseMap(
                mapOf(
                    "secret" to secret as? Map<String, Any>
                )
            )
        ).getString("secret")
}