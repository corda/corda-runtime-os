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

    /**
     * Constants defining keys for the filter in the lookup function of Signing Service.
     */
    object SigningKeyFilters {
        /**
         * The HSM's category which handles keys.
         */
        const val CATEGORY_FILTER = "category"

        /**
         * The key's signature scheme name.
         */
        const val SCHEME_CODE_NAME_FILTER = "schemeCodeName"

        /**
         * The alias which is assigned by the tenant.
         */
        const val ALIAS_FILTER = "alias"

        /**
         * The wrapping key alias.
         */
        const val MASTER_KEY_ALIAS_FILTER = "masterKeyAlias"

        /**
         * The id associated with a key.
         */
        const val EXTERNAL_ID_FILTER = "externalId"

        /**
         * Inclusive time after which a key was created.
         */
        const val CREATED_AFTER_FILTER = "createdAfter"

        /**
         * Inclusive time before which a key was created.
         */
        const val CREATED_BEFORE_FILTER = "createdBefore"
    }
}