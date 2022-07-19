package net.corda.crypto.impl.config

import net.corda.libs.configuration.SmartConfig

const val KEY_MAP_TRANSIENT_NAME = "TRANSIENT"
const val KEY_MAP_CACHING_NAME = "CACHING"
const val WRAPPING_DEFAULT_NAME = "DEFAULT"
const val WRAPPING_HSM_NAME = "HSM"

/**
 * The Soft HSM configuration.
 *
 *  "softHSM": {
 *      "maxAttempts": 3,
 *      "attemptTimeoutMills": 20000,
 *      "salt": "<plain-text-value>",
 *          "passphrase": {
 *          "configSecret": {
 *              "encryptedSecret": "<encrypted-value>"
 *          }
 *      },
 *      "keyMap": {
 *          "name": "TRANSIENT|CACHING",
 *          "cache": {
 *              "expireAfterAccessMins": 60,
 *              "maximumSize": 1000
 *          }
 *      },
 *      "wrappingKeyMap": {
 *          "name": "TRANSIENT|CACHING",
 *          "cache": {
 *              "expireAfterAccessMins": 60,
 *              "maximumSize": 100
 *          }
 *      },
 *      "wrapping": {
 *          "name": "DEFAULT|HSM",
 *          "hsm": {
 *              "name": ".."
 *              "config": {
 *              }
 *          }
 *      }
 *  }
 */
class CryptoSoftHSMConfig(internal val config: SmartConfig) : SmartConfig by config {
    class Cache(internal val config: SmartConfig) : SmartConfig by config {
        val expireAfterAccessMins: Long
            get() = try {
                config.getLong(this::expireAfterAccessMins.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::expireAfterAccessMins.name}", e)
            }

        val maximumSize: Long
            get() = try {
                config.getLong(this::maximumSize.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::maximumSize.name}", e)
            }
    }

    class Map(internal val config: SmartConfig) : SmartConfig by config {
        val name: String
            get() = try {
                config.getString(this::name.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::name.name}", e)
            }

        val cache: Cache
            get() = try {
                Cache(config.getConfig(this::cache.name))
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::cache.name}", e)
            }
    }

    class Wrapping(internal val config: SmartConfig) : SmartConfig by config {
        val name: String
            get() = try {
                config.getString(this::name.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::name.name}", e)
            }

        val hsm: SmartConfig
            get() = try {
                config.getConfig(this::hsm.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::hsm.name}", e)
            }
    }

    val maxAttempts: Int
        get() = try {
            config.getInt(this::maxAttempts.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maxAttempts.name}", e)
        }

    val attemptTimeoutMills: Long
        get() = try {
            config.getLong(this::attemptTimeoutMills.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::attemptTimeoutMills.name}", e)
        }

    val salt: String
        get() = try {
            config.getString(this::salt.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::salt.name}", e)
        }

    val passphrase: String
        get() = try {
            config.getString(this::passphrase.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::passphrase.name}", e)
        }

    val keyMap: Map
        get() = try {
            Map(config.getConfig(this::keyMap.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::keyMap.name}", e)
        }

    val wrappingKeyMap: Map
        get() = try {
            Map(config.getConfig(this::wrappingKeyMap.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::wrappingKeyMap.name}", e)
        }

    val wrapping: Wrapping
        get() = try {
            Wrapping(config.getConfig(this::wrapping.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::wrapping.name}", e)
        }
}