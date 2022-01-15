package net.corda.crypto.component.persistence

import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence

/**
 * Defines a factory to get persistence implementation for signing keys.
 */
interface SigningKeysPersistenceProvider {
    /**
     * Unique name for factory
     */
    val name: String

    /**
     * Returns an instance per tenant. After the instance is created it's cached.
     */
    fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord>
}