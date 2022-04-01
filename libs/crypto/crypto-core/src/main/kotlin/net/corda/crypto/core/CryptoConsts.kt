package net.corda.crypto.core

/**
 * Common crypto constants.
 */
object CryptoConsts {
    const val CLUSTER_TENANT_ID = "cluster"

    /**
     * Constants defining HSM categories.
     */
    object HsmCategories {
        const val FRESH_KEYS = "FRESH_KEYS"
        const val LEDGER = "LEDGER"
        const val NOTARY = "NOTARY"
        const val SESSION = "SESSION"
        const val TLS = "TLS"
    }
}