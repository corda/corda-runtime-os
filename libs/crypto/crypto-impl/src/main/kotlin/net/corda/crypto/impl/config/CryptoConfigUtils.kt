package net.corda.crypto.impl.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.crypto.failures.CryptoException
import java.util.UUID

/*
{
    "cryptoConnectionFactory: {
        "connectionsExpireAfterAccessMins": 5,
        "connectionNumberLimit": 3
    },
    "signingService": {
        "keyCache": {
            "expireAfterAccessMins": 60,
            "maximumSize": 1000
        }
    },
    "workerSetId": "SOFT",
    "workerSets": {
        "SOFT": {
            "topicSuffix": "",
            "retry" : {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "SOFT",
                "cfg": {
                    "salt": "<plain-text-value>",
                    "passphrase": {
                        "configSecret": {
                            "encryptedSecret": "<encrypted-value>"
                        }
                    },
                    "keyMap": {
                        "name": "CACHING",
                        "cache": {
                            "expireAfterAccessMins": 60,
                            "maximumSize": 1000
                        }
                    },
                    "wrappingKeyMap": {
                        "name": "CACHING",
                        "cache": {
                            "expireAfterAccessMins": 60,
                            "maximumSize": 1000
                        }
                    },
                    "wrapping": {
                        "name": "DEFAULT",
                        "hsm": {
                            "name": ".."
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
                "name": "SOFT",
                "config": {
                }
        }
    },



    "hsmPersistence": {
        "expireAfterAccessMins": 240,
        "maximumSize": 1000,
        "downstreamMaxAttempts": 3
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
private const val WORKER_SET_ID = "workerSetId"
private const val WORKER_SET_OBJ = "workerSets.%s"
private const val DEFAULT_WORKER_SET_ID = "SOFT"
private val DEFAULT_WORKER_SET_OBJ = String.format(WORKER_SET_OBJ, DEFAULT_WORKER_SET_ID)
private val MASTER_WRAPPING_KEY_SALT = DEFAULT_WORKER_SET_OBJ +
        CryptoWorkerSetConfig::hsm.name +
        CryptoWorkerSetConfig.HSMConfig::cfg.name +
        "wrappingKeyMap.salt"
private val MASTER_WRAPPING_KEY_PASSPHRASE = DEFAULT_WORKER_SET_OBJ +
        CryptoWorkerSetConfig::hsm.name +
        CryptoWorkerSetConfig.HSMConfig::cfg.name +
        "wrappingKeyMap.passphrase"


private const val HSM_PERSISTENCE_OBJ = "hsmPersistence"
private const val BUS_PROCESSORS_OBJ = "bus.processors"
private const val OPS_BUS_PROCESSOR_OBJ = "ops"
private const val FLOW_BUS_PROCESSOR_OBJ = "flow"
private const val HSM_CONFIG_BUS_PROCESSOR_OBJ = "config"
private const val HSM_REGISTRATION_BUS_PROCESSOR_OBJ = "registration"

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
                        CryptoConnectionsFactoryConfig::connectionsExpireAfterAccessMins.name to "5",
                        CryptoConnectionsFactoryConfig::connectionNumberLimit.name to "3"
                    )
                )
            )
            .withValue(
                SIGNING_SERVICE_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoSigningServiceConfig::keyCache.name to mapOf(
                            CryptoSigningServiceConfig.CacheConfig::expireAfterAccessMins.name to "60",
                            CryptoSigningServiceConfig.CacheConfig::maximumSize.name to "1000"
                        )
                    )
                )
            )
            .withValue(WORKER_SET_ID, ConfigValueFactory.fromAnyRef("SOFT"))
            .withValue(
                DEFAULT_WORKER_SET_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoWorkerSetConfig::topicSuffix.name to "",
                        CryptoWorkerSetConfig::retry.name to mapOf(
                            CryptoWorkerSetConfig.RetryConfig::maxAttempts.name to "3",
                            CryptoWorkerSetConfig.RetryConfig::attemptTimeoutMills.name to "20000",
                        ),
                        CryptoWorkerSetConfig::hsm.name to mapOf(
                            CryptoWorkerSetConfig.HSMConfig::name.name to "SOFT",
                            CryptoWorkerSetConfig.HSMConfig::cfg.name to mapOf(
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
                                    "name" to "DEFAULT",
                                    "hsm" to emptyMap<String, String>()
                                )
                            )
                        )
                    )
                )
            )


            .withValue(
                HSM_PERSISTENCE_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoHSMPersistenceConfig::expireAfterAccessMins.name to "240",
                        CryptoHSMPersistenceConfig::maximumSize.name to "1000",
                        CryptoHSMPersistenceConfig::downstreamMaxAttempts.name to "3",
                    )
                )
            )
            .withValue(
                BUS_PROCESSORS_OBJ, ConfigValueFactory.fromMap(
                    mapOf(
                        OPS_BUS_PROCESSOR_OBJ to mapOf(
                            BusProcessorConfig::maxAttempts.name to "3",
                            BusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        FLOW_BUS_PROCESSOR_OBJ to mapOf(
                            BusProcessorConfig::maxAttempts.name to "3",
                            BusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        HSM_CONFIG_BUS_PROCESSOR_OBJ to mapOf(
                            BusProcessorConfig::maxAttempts.name to "3",
                            BusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        ),
                        HSM_REGISTRATION_BUS_PROCESSOR_OBJ to mapOf(
                            BusProcessorConfig::maxAttempts.name to "3",
                            BusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(listOf(200)),
                        )
                    ),
                )
            )
    )
} catch (e: Throwable) {
    throw CryptoException("Failed to create default crypto config", e)
}

fun Map<String, SmartConfig>.toCryptoConfig(): SmartConfig =
    this[CRYPTO_CONFIG] ?: throw IllegalStateException(
        "Could not generate a crypto configuration due to missing key: $CRYPTO_CONFIG"
    )

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

fun SmartConfig.workerSetId(): String =
    try {
        getString(WORKER_SET_ID)
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $WORKER_SET_ID.", e)
    }

fun SmartConfig.workerSet(id: String): CryptoWorkerSetConfig {
    val path = String.format(WORKER_SET_OBJ, id)
    return try {
        CryptoWorkerSetConfig(getConfig(path))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $path.", e)
    }
}




fun SmartConfig.hsmPersistence(): CryptoHSMPersistenceConfig =
    try {
        CryptoHSMPersistenceConfig(getConfig(HSM_PERSISTENCE_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get CryptoHSMPersistenceConfig.", e)
    }

fun SmartConfig.opsBusProcessor(): BusProcessorConfig =
    try {
        BusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(OPS_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for ops operations.", e)
    }

fun SmartConfig.flowBusProcessor(): BusProcessorConfig =
    try {
        BusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(FLOW_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for flow ops operations.", e)
    }

fun SmartConfig.hsmConfigBusProcessor(): BusProcessorConfig =
    try {
        BusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(HSM_CONFIG_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for hsm config operations.", e)
    }

fun SmartConfig.hsmRegistrationBusProcessor(): BusProcessorConfig =
    try {
        BusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(HSM_REGISTRATION_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for hsm registration operations.", e)
    }
