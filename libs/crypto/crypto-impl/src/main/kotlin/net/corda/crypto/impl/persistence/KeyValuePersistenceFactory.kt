package net.corda.crypto.impl.persistence

import net.corda.crypto.SigningService
import net.corda.crypto.impl.soft.SoftCryptoService

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

