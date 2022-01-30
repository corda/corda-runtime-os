package net.corda.crypto.persistence

import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.lifecycle.Lifecycle

/**
 * Defines a factory to get persistence implementation for signing keys.
 */
interface SigningKeysPersistenceProvider : Lifecycle {
    /**
     * Returns an instance per tenant. After the instance is created it's cached.
     */
    fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>
}