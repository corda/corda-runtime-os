package net.corda.crypto.softhsm

const val KEY_MAP_TRANSIENT_NAME = "TRANSIENT"
const val KEY_MAP_CACHING_NAME = "CACHING"
const val WRAPPING_DEFAULT_NAME = "DEFAULT"
const val WRAPPING_HSM_NAME = "HSM"

/**
 * The Soft HSM configuration. Note that the whole data is encrypted by the platform.
 *
 * The device specific configuration:
 *  {
 *      "keyMap": {
 *          "name": "TRANSIENT|CACHING",
 *          "cache": {
 *              "expireAfterAccessMins": 60,
 *              "maximumSize": 1000
 *          }
 *      },
 *      "wrappingKeyMap": {
 *          "name": "TRANSIENT|CACHING",
 *          "salt": "<plain-text-value>",
 *          "passphrase": {
 *              "configSecret": {
 *                  "encryptedSecret": "<encrypted-value>"
 *              }
 *          },
 *          "cache": {
 *              "expireAfterAccessMins": 60,
 *              "maximumSize": 100
 *          }
 *      },
 *      "wrapping": {
 *          "name": "DEFAULT|HSM",
 *          "hsm": {
 *              "name": ".."
 *              "cfg": {
 *              }
 *          }
 *      }
 *  }
 *
 * This values are defined on the level above:
 *      "maxAttempts": 3,
 *      "attemptTimeoutMills": 20000,
 */
class SoftCryptoServiceConfig(
    val keyMap: SoftKeyMapConfig,
    val wrappingKeyMap: SoftWrappingKeyMapConfig,
    val wrapping: SoftWrappingConfig
)

class SoftCacheConfig(
    val expireAfterAccessMins: Long,
    val maximumSize: Long
)

class SoftKeyMapConfig(
    val name: String,
    val cache: SoftCacheConfig
)

class SoftWrappingKeyMapConfig(
    val name: String,
    val salt: String,
    val passphrase: String,
    val cache: SoftCacheConfig
)

class SoftWrappingConfig(
    val name: String,
    val hsm: SoftWrappingHSMConfig?
)

class SoftWrappingHSMConfig(
    val name: String?,
    val cfg: Map<String, Any>
)