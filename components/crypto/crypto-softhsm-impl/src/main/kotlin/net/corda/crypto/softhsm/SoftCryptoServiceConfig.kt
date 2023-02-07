package net.corda.crypto.softhsm

const val KEY_MAP_TRANSIENT_NAME = "TRANSIENT"
const val KEY_MAP_CACHING_NAME = "CACHING"
const val WRAPPING_DEFAULT_NAME = "DEFAULT"
const val WRAPPING_HSM_NAME = "HSM"

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