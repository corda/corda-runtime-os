package net.corda.crypto.persistence

import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.lifecycle.Lifecycle

/**
 * Defines a factory to get persistence implementation for soft HSM keys.
 */
interface SoftKeysPersistenceProvider : Lifecycle {
    /**
     * Returns an instance per tenant. After the instance is created it's cached.
     */
    fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
    ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord>
}

