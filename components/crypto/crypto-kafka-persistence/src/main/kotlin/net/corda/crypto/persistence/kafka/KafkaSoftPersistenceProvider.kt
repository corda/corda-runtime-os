package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.SoftCryptoKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.component.persistence.SoftPersistenceProvider
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence

class KafkaSoftPersistenceProvider : SoftPersistenceProvider {
    override val name: String = "kafka"

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> {
        TODO("Not yet implemented")
    }
}