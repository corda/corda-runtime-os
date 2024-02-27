@file:Suppress("TooManyFunctions")

package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.cipher.suite.ConfigurationSecrets
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

const val HSM_ID = "hsmId"
const val EXPIRE_AFTER_ACCESS_MINS = "expireAfterAccessMins"
const val MAXIMUM_SIZE = "maximumSize"
const val DEFAULT = "default"
const val CACHING = "caching"
const val RETRYING = "retrying"

const val HSM = "hsm"

// fields of HSM config
const val WRAPPING_KEYS = "wrappingKeys"
const val DEFAULT_WRAPPING_KEY = "defaultWrappingKey"

// fields of wrappingKekys objects
const val ALIAS = "alias"
const val SALT = "salt"
const val PASSPHRASE = "passphrase"

fun Map<String, SmartConfig>.toCryptoConfig(): SmartConfig =
    this[CRYPTO_CONFIG] ?: throw IllegalStateException(
        "Could not generate a crypto configuration due to missing key: $CRYPTO_CONFIG"
    )

fun SmartConfig.toConfigurationSecrets(): ConfigurationSecrets = ConfigurationSecretsImpl(this)

fun SmartConfig.signingService(): CryptoSigningServiceConfig =
    CryptoSigningServiceConfig(this)

fun SmartConfig.hsm(): CryptoHSMConfig {
    return try {
        CryptoHSMConfig(getConfig(HSM))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $HSM.", e)
    }
}

fun SmartConfig.opsBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(RETRYING))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for ops operations.", e)
    }

fun SmartConfig.retrying(): RetryingConfig =
    try {
        RetryingConfig(getConfig(RETRYING))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get $RETRYING.", e)
    }



fun SmartConfig.flowBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(RETRYING))
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to get BusProcessorConfig for flow ops operations.", e)
    }

fun SmartConfig.hsmRegistrationBusProcessor(): CryptoBusProcessorConfig =
    try {
        CryptoBusProcessorConfig(getConfig(RETRYING))
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


data class KeyDerivationParameters(
    val passphrase: Any,
    val salt: Any
)

// TODO - get this from the JSON config schema, or eliminate this function
@Suppress("LongMethod")
fun createDefaultCryptoConfig(
    wrappingKeys: List<KeyDerivationParameters>,
): SmartConfig =
    SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
        .withValue(
            CACHING,
            ConfigValueFactory.fromMap(
                mapOf(
                    CacheConfig::expireAfterAccessMins.name to mapOf(
                        DEFAULT to 60
                    ),
                    CacheConfig::maximumSize.name to mapOf(
                        DEFAULT to 10000
                    )
                )
            )
        )
        .withValue(
            HSM, ConfigValueFactory.fromMap(
                mapOf(
                    CryptoHSMConfig::retrying.name to mapOf(
                        CryptoHSMConfig.RetryConfig::maxAttempts.name to 3,
                        CryptoHSMConfig.RetryConfig::attemptTimeoutMills.name to 20000,
                    ),
                    CryptoHSMConfig::defaultWrappingKey.name to ConfigValueFactory.fromAnyRef("root1"),
                    CryptoHSMConfig::wrappingKeys.name to
                        wrappingKeys.withIndex().map { (i, params) ->
                            ConfigValueFactory.fromAnyRef(
                                mapOf(
                                    "alias" to "root${i+1}",
                                    "salt" to params.salt,
                                    "passphrase" to params.passphrase,
                                )
                            )
                        }.toList()
                )
            )
        )
        .withValue(
            RETRYING,
            ConfigValueFactory.fromMap(
                mapOf(
                    RetryingConfig::maxAttempts.name to mapOf(
                        DEFAULT to 3
                    ),
                    RetryingConfig::waitBetweenMills.name to mapOf(
                        DEFAULT to ConfigValueFactory.fromIterable(listOf(200))
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