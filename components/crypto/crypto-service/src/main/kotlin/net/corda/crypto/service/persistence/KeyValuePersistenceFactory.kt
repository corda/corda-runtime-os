package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence

/**
 * Defines a factory to new instances implementing [KeyValuePersistence]
 */
interface KeyValuePersistenceFactory {
    /**
     * Unique name for factory
     */
    val name: String

   /**
     * Creates a new instance of the key/value persistence for [SigningService].
     */
    fun createSigningPersistence(
       tenantId: String,
       mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord>

    /**
     * Creates a new instance of the key/value persistence for [SoftCryptoService].
     */
    fun createDefaultCryptoPersistence(
        tenantId: String,
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
}

