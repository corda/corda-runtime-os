package net.corda.crypto.persistence.inmemory

import net.corda.crypto.impl.persistence.InMemoryKeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.KeyValuePersistenceFactory
import net.corda.crypto.component.persistence.SigningKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecordInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistence used by the dev service
 */
class InMemoryKeyValuePersistenceFactory : KeyValuePersistenceFactory {
    companion object {
        const val NAME = "dev"
    }

    private val signingData =
        ConcurrentHashMap<String, SigningKeyRecord>()

    private val cryptoData =
        ConcurrentHashMap<String, SoftCryptoKeyRecordInfo>()

    override val name: String = NAME

    override fun createSigningPersistence(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord> {
        return InMemoryKeyValuePersistence(
            mutator = mutator,
            data = signingData
        )
    }

    override fun createDefaultCryptoPersistence(
        tenantId: String,
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> {
        return InMemoryKeyValuePersistence(
            mutator = mutator,
            data = cryptoData
        )
    }
}