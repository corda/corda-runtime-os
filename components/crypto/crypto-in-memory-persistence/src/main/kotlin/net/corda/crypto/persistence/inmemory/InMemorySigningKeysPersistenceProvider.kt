package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.data.crypto.persistence.SigningKeysRecord
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningKeysPersistenceProvider::class])
class InMemorySigningKeysPersistenceProvider : SigningKeysPersistenceProvider {
    companion object {
        const val NAME = "dev"
    }

    private val instances =
        ConcurrentHashMap<String, InMemoryStore>()

    override val name: String = NAME

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryStore(
                mutator = mutator,
                data = ConcurrentHashMap<String, SigningKeysRecord>()
            )
        }

    private class InMemoryStore(
        private val data: ConcurrentHashMap<String, SigningKeysRecord>,
        private val mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ) : KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>, AutoCloseable {

        override fun put(entity: SigningKeysRecord, vararg key: EntityKeyInfo): SigningKeysRecord {
            val value = mutator.mutate(entity)
            key.forEach {
                data[it.key] = value
            }
            return value
        }

        override fun get(key: String): SigningKeysRecord? =
            data[key]

        override fun close() {
            data.clear()
        }
    }
}