package net.corda.crypto.impl.persistence

import net.corda.crypto.SigningService
import net.corda.crypto.impl.DefaultCryptoService

/**
 * Defines a factory to new instances implementing [KeyValuePersistence]
 */
interface KeyValuePersistenceFactory {
   /**
     * Creates a new instance of the key/value persistence for [SigningService] and [FreshKeySigningService].
     */
    fun createSigningPersistence(
        memberId: String,
        mutator: KeyValueMutator<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    ): KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>

    /**
     * Creates a new instance of the key/value persistence for [DefaultCryptoService].
     */
    fun createDefaultCryptoPersistence(
        memberId: String,
        mutator: KeyValueMutator<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    ): KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
}

