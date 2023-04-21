package net.corda.crypto.config.impl

/**
 * Defines how wrapping key should be used for each tenant.
 */
enum class MasterKeyPolicy {
    /**
     * No action related to wrapping key generation.
     */
    NONE,

    /**
     * All tenants for that instance will use the same wrapping key.
     */
    SHARED,

    /**
     * Each tenant will get auto generated wrapping key.
     */
    UNIQUE
}