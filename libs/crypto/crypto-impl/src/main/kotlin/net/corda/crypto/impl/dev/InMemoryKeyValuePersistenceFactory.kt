package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.impl.persistence.SoftCryptoKeyRecord
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningKeyRecord
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [KeyValuePersistenceFactory::class])
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