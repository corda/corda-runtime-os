package net.corda.crypto.persistence.inmemory

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
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SigningKeysRecord, SigningKeysRecord>>()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SigningKeysRecord>()
            )
        }

    override val isRunning: Boolean = true

    override fun start() {
    }

    override fun stop() {
        instances.clear()
    }
}