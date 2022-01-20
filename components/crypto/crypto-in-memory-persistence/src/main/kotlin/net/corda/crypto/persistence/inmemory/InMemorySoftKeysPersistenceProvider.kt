package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.CachedSoftKeysRecord
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.data.crypto.persistence.SoftKeysRecord
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SoftKeysPersistenceProvider::class])
class InMemorySoftKeysPersistenceProvider : SoftKeysPersistenceProvider {
    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord>>()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
    ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, CachedSoftKeysRecord>()
            )
        }

    override var isRunning: Boolean = true

    override fun start() {
    }

    override fun stop() {
        instances.clear()
    }
}