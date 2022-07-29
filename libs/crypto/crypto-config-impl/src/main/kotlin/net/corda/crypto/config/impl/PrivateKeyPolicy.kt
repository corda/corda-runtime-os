package net.corda.crypto.config.impl

/**
 * Defines the key generation policy, helps with allocating the HSM.
 */
enum class PrivateKeyPolicy {
    /**
     * The private key is stored in HSM. The tenant's assignment is counted towards the quota.
     */
    ALIASED,

    /**
     * The private key is wrapped and exported from HSM, the key material is persisted on Corda side.
     */
    WRAPPED,

    /**
     * The decision to store the key or not in the HSM is taken dynamically.
     * The tenant's assignment is counted towards the quota.
     */
    BOTH
}