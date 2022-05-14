package net.corda.crypto.core

/**
 * Common crypto constants.
 */
object CryptoConsts {
    const val SOFT_HSM_CONFIG_ID = "soft-hsm-config"
    const val SOFT_HSM_SERVICE_NAME = "soft"

    /**
     * Constants defining HSM categories.
     */
    object Categories {
        const val ACCOUNTS = "ACCOUNTS"
        const val CI = "CI"
        const val LEDGER = "LEDGER"
        const val NOTARY = "NOTARY"
        const val SESSION_INIT = "SESSION_INIT"
        const val TLS = "TLS"
        const val JWT_KEY = "JWT_KEY"

        val all: Set<String> = setOf(
            ACCOUNTS,
            CI,
            LEDGER,
            NOTARY,
            SESSION_INIT,
            TLS,
            JWT_KEY
        )
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

    /**
     * Constants defining keys for the filter in the lookup function of HSM services.
     */
    object HSMFilters {
        /**
         * The key's signature scheme name.
         */
        const val SERVICE_NAME_FILTER = "serviceName"
    }

    /**
     * Constants defining keys for the context in the HSM services functions.
     */
    object HSMContext {
        /**
         * The optional preferred private key policy, accepted values are NONE, ALIASED
         */
        const val PREFERRED_PRIVATE_KEY_POLICY_KEY = "preferredPrivateKeyPolicy"
        const val PREFERRED_PRIVATE_KEY_POLICY_NONE = "NONE"
        const val PREFERRED_PRIVATE_KEY_POLICY_ALIASED = "ALIASED"
    }
}