package net.corda.crypto.persistence.db.impl

import net.corda.data.crypto.persistence.SoftKeysRecord

class DbSoftKeysPersistenceProvider(

) : SoftKeysPersistenceProvider {
    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
    ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

}