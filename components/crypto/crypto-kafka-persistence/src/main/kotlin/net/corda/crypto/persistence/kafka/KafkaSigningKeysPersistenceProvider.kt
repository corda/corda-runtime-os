package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.SigningKeyRecord
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence

class KafkaSigningKeysPersistenceProvider : SigningKeysPersistenceProvider {
    override val name: String = "kafka"

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord> {
        TODO("Not yet implemented")
    }
}