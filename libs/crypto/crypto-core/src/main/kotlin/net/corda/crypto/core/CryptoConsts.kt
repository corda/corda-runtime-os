package net.corda.crypto.core

/**
 * Common crypto constants.
 */
object CryptoConsts {
    const val SOFT_HSM_ID = "SOFT"
    const val SOFT_HSM_SERVICE_NAME = "SOFT"

    /**
     * Constants defining HSM categories.
     */
    object Categories {
        const val ACCOUNTS = "ACCOUNTS"
        const val CI = "CI"
        const val LEDGER = "LEDGER"
        const val NOTARY = "NOTARY"
        const val PRE_AUTH = "PRE_AUTH"
        const val SESSION_INIT = "SESSION_INIT"
        const val TLS = "TLS"
        const val JWT = "JWT_KEY"
        const val ENCRYPTION_SECRET = "ENCRYPTION_SECRET"

        enum class KeyCategory(val value: String) {
            ACCOUNTS_KEY(ACCOUNTS),
            CI_KEY(CI),
            LEDGER_KEY(LEDGER),
            NOTARY_KEY(NOTARY),
            PRE_AUTH_KEY(PRE_AUTH),
            SESSION_INIT_KEY(SESSION_INIT),
            TLS_KEY(TLS),
            JWT_KEY(JWT),
            ENCRYPTION_SECRET_KEY(ENCRYPTION_SECRET)
        }

        val all: Set<String> = CryptoConsts.Categories.KeyCategory.values().map { it.value }.toSet()
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

    // TODO The below seems to only be relevant to hardware HSMs
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