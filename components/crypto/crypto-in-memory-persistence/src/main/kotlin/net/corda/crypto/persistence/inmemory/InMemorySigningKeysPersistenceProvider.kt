package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.SigningKeyRecord
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.impl.persistence.InMemoryKeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningKeysPersistenceProvider::class])
class InMemorySigningKeysPersistenceProvider : SigningKeysPersistenceProvider {
    companion object {
        const val NAME = "dev"
    }

    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SigningKeyRecord, SigningKeyRecord>>()

    override val name: String = NAME

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SigningKeyRecord>()
            )
        }
}