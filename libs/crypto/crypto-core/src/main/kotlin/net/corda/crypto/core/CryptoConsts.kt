package net.corda.crypto.core

/**
 * Common crypto constants.
 */
object CryptoConsts {
    const val CLUSTER_TENANT_ID = "cluster"

    /**
     * Constants defining most common categories for the signing service.
     */
    object Categories {
        const val LEDGER = "LEDGER"
        const val FRESH_KEYS = "FRESH_KEYS"
        const val AUTHENTICATION = "AUTHENTICATION"
        const val TLS = "TLS"
    }
}