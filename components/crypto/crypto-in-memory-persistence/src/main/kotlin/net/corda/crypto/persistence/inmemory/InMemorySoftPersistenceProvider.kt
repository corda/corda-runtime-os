package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SoftKeysRecordInfo
import net.corda.crypto.component.persistence.SoftPersistenceProvider
import net.corda.data.crypto.persistence.SoftKeysRecord
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SoftPersistenceProvider::class])
class InMemorySoftPersistenceProvider : SoftPersistenceProvider {
    companion object {
        const val NAME = "dev"
    }
    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord>>()

    override val name: String = NAME

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SoftKeysRecordInfo, SoftKeysRecord>
    ): KeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SoftKeysRecordInfo>()
            )
        }
}