package net.corda.crypto.persistence.inmemory

import net.corda.crypto.impl.persistence.InMemoryKeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SoftCryptoKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.component.persistence.SoftPersistenceProvider
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SoftPersistenceProvider::class])
class SoftInMemoryPersistenceProvider : SoftPersistenceProvider {
    companion object {
        const val NAME = "dev"
    }
    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>>()

    override val name: String = NAME

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SoftCryptoKeyRecordInfo>()
            )
        }
}