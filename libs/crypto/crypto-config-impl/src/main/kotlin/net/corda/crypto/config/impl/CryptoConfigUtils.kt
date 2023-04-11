@file:Suppress("TooManyFunctions")

package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.cipher.suite.ConfigurationSecrets
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG

// NOTE: hsmId is part of the bootstrap configuration

/*
{
    "cryptoConnectionFactory": {
        "expireAfterAccessMins": 5,
        "maximumSize": 3
    },
    "signingService": {
        "cache": {
            "expireAfterAccessMins": 60,
            "maximumSize": 10000
        }
    },
    "hsmService": {
        "downstreamMaxAttempts": 3
    },
    "hsmMap": {
        "SOFT": {
            "workerTopicSuffix": "",
            "retry": {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "SOFT",
                "categories": [
                    {
                        "category": "*",
                        "policy": "WRAPPED"
                    }
                ],
                "masterKeyPolicy": "UNIQUE",
                "capacity": -1,
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
                    "defaultWrappingKey" : "root1",
                    "wrappingKeys" : [
                         {"alias": "root1", "passphrase": "B"}
                    ],
                    "wrappingKeyMap": {
                        "name": "CACHING",
                        "cache": {
                            "expireAfterAccessMins": 60,
                            "maximumSize": 100
                        }
                    },
                    "wrapping": {
                        "name": "DEFAULT",
                        "hsm": {
                            "name": "..",
                            "cfg": {}
                        }
                    },
                }
            }
        },
        "AWS": {
            "retry": {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "AWS",
                "categories": [
                    {
                        "category": "ACCOUNTS",
                        "policy": "WRAPPED"
                    },
                    {
                        "category": "CI",
                        "policy": "WRAPPED"
                    },
                    {
                        "category": "*",
                        "policy": "ALIASED"
                    }
                ],
                "masterKeyPolicy": "SHARED",
                "masterKeyAlias": "cordawrappingkey",
                "capacity": 3300,
                "supportedSchemes": [
                    "CORDA.RSA",
                    "CORDA.ECDSA.SECP256R1",
                    "CORDA.ECDSA.SECP256K1",
                    "CORDA.X25519"
                ],
                "cfg": {
                    "username": "user",
                    "partition": "whatever"
                }
            }
        }
    },
    "busProcessors": {
        "ops": {
            "maxAttempts": 3,
            "waitBetweenMills": [
                200
            ]
        },
        "flow": {
            "maxAttempts": 3,
            "waitBetweenMills": [
                200
            ]
        },
        "registration": {
            "maxAttempts": 3,
            "waitBetweenMills": [
                200
            ]
        }
    }
}
 */

private const val HSM_ID = "hsmId"
const val CRYPTO_CONNECTION_FACTORY_OBJ = "cryptoConnectionFactory"
const val EXPIRE_AFTER_ACCESS_MINS = "expireAfterAccessMins"
const val MAXIMUM_SIZE = "maximumSize"
private const val SIGNING_SERVICE_OBJ = "signingService"
private const val HSM_SERVICE_OBJ = "hsmService"
private const val HSM_MAP = "hsmMap"
private const val HSM_MAP_ITEM_OBJ = "hsmMap.%s"
private const val BUS_PROCESSORS_OBJ = "busProcessors"
private const val OPS_BUS_PROCESSOR_OBJ = "ops"
private const val FLOW_BUS_PROCESSOR_OBJ = "flow"
private const val HSM_REGISTRATION_BUS_PROCESSOR_OBJ = "registration"
private val DEFAULT_HSM_OBJ = String.format(HSM_MAP_ITEM_OBJ, SOFT_HSM_ID)

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

fun SmartConfig.hsmRegistrationBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(BUS_PROCESSORS_OBJ).getConfig(HSM_REGISTRATION_BUS_PROCESSOR_OBJ))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for hsm registration operations.", e)
    }

fun SmartConfig.bootstrapHsmId(): String =
    try {
        getString(HSM_ID)
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $HSM_ID.", e)
    }

fun createCryptoBootstrapParamsMap(hsmId: String): Map<String, String> =
    mapOf(HSM_ID to hsmId)

// TODO - get this from the JSON config schema, or eliminate this function
@Suppress("LongMethod")
fun createDefaultCryptoConfig(wrappingKeyPassphrase: Any, wrappingKeySalt: Any): SmartConfig =
    SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
        .withValue(
            CRYPTO_CONNECTION_FACTORY_OBJ, ConfigValueFactory.fromMap(
                mapOf(
                    CryptoConnectionsFactoryConfig::expireAfterAccessMins.name to 5,
                    CryptoConnectionsFactoryConfig::maximumSize.name to 3
                )
            )
        )
        .withValue(
            SIGNING_SERVICE_OBJ, ConfigValueFactory.fromMap(
                mapOf(
                    CryptoSigningServiceConfig::cache.name to mapOf(
                        CryptoSigningServiceConfig.CacheConfig::expireAfterAccessMins.name to 60,
                        CryptoSigningServiceConfig.CacheConfig::maximumSize.name to 10000
                    )
                )
            )
        )
        .withValue(
            HSM_SERVICE_OBJ, ConfigValueFactory.fromMap(
                mapOf(
                    CryptoHSMServiceConfig::downstreamMaxAttempts.name to 3
                )
            )
        )
        .withValue(
            DEFAULT_HSM_OBJ, ConfigValueFactory.fromMap(
                mapOf(
                    CryptoHSMConfig::workerTopicSuffix.name to "",
                    CryptoHSMConfig::retry.name to mapOf(
                        CryptoHSMConfig.RetryConfig::maxAttempts.name to 3,
                        CryptoHSMConfig.RetryConfig::attemptTimeoutMills.name to 20000,
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
                        CryptoHSMConfig.HSMConfig::capacity.name to -1,
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
                        CryptoHSMConfig.HSMConfig::cfg.name to ConfigValueFactory.fromMap(
                            mapOf(
                                "defaultWrappingKey" to ConfigValueFactory.fromAnyRef("root1"),
                                "wrappingKeys" to listOf(
                                    ConfigValueFactory.fromAnyRef(
                                        mapOf(
                                            "alias" to "root1",
                                            "salt" to wrappingKeySalt,
                                            "passphrase" to wrappingKeyPassphrase,
                                        )
                                    )
                                ),
                                "keyMap" to ConfigValueFactory.fromMap(
                                    mapOf(
                                        "name" to "CACHING",
                                        "cache" to ConfigValueFactory.fromMap(
                                            mapOf(
                                                "expireAfterAccessMins" to 60,
                                                "maximumSize" to 1000
                                            )
                                        )

                                    )
                                ),
                                "wrappingKeyMap" to mapOf(
                                    "name" to "CACHING",
                                    "cache" to mapOf(
                                        "expireAfterAccessMins" to 60,
                                        "maximumSize" to 1000
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
        )
        .withValue(
            BUS_PROCESSORS_OBJ, ConfigValueFactory.fromMap(
                mapOf(
                    OPS_BUS_PROCESSOR_OBJ to mapOf(
                        CryptoBusProcessorConfig::maxAttempts.name to 3,
                        CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(
                            listOf(
                                200
                            )
                        ),
                    ),
                    FLOW_BUS_PROCESSOR_OBJ to mapOf(
                        CryptoBusProcessorConfig::maxAttempts.name to 3,
                        CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(
                            listOf(
                                200
                            )
                        ),
                    ),
                    HSM_REGISTRATION_BUS_PROCESSOR_OBJ to mapOf(
                        CryptoBusProcessorConfig::maxAttempts.name to 3,
                        CryptoBusProcessorConfig::waitBetweenMills.name to ConfigValueFactory.fromIterable(
                            listOf(
                                200
                            )
                        ),
                    )
                ),
            )
    )


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