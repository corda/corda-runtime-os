package net.corda.crypto

/**
 * Common crypto constants.
 */
object CryptoConsts {
    const val CLUSTER_TENANT_ID = "cluster"

    /**
     * Crypto request context constants.
     */
    object Request {
        /**
         * Key for the key pair defining HSM's label in the
         */
        const val HSM_LABEL_CONTEXT_KEY = "hsm.label"
    }

    /**
     * Constants defining most common categories for the signing service.
     */
    object CryptoCategories {
        const val LEDGER = "LEDGER"
        const val FRESH_KEYS = "FRESH_KEYS"
        const val AUTHENTICATION = "AUTHENTICATION"
        const val TLS = "TLS"
    }
}