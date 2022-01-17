package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.EntityKeyInfo
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
        ConcurrentHashMap<String, InMemoryStore>()

    override val name: String = NAME

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SoftKeysRecordInfo, SoftKeysRecord>
    ): KeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryStore(
                mutator = mutator,
                data = ConcurrentHashMap<String, SoftKeysRecordInfo>()
            )
        }

    private class InMemoryStore(
        private val data: ConcurrentHashMap<String, SoftKeysRecordInfo>,
        private val mutator: KeyValueMutator<SoftKeysRecordInfo, SoftKeysRecord>
    ) : KeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord>, AutoCloseable {

        override fun put(entity: SoftKeysRecord, vararg key: EntityKeyInfo): SoftKeysRecordInfo {
            val value = mutator.mutate(entity)
            key.forEach {
                data[it.key] = value
            }
            return value
        }

        override fun get(key: String): SoftKeysRecordInfo? =
            data[key]

        override fun close() {
            data.clear()
        }
    }
}