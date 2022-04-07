package net.corda.crypto.persistence.db.impl

import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SigningKeysPersistenceProvider::class])
class DbSigningKeysPersistenceProvider(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : AbstractPersistenceProvider<DbSigningKeysPersistenceProvider.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SigningKeysPersistenceProvider>(),
    InactiveImpl(),
    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
), SigningKeysPersistenceProvider {

    interface Impl : AutoCloseable {
        fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
        ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>
    }

    override fun createActiveImpl(): Impl = ActiveImpl(dbConnectionManager)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        impl.getInstance(tenantId, mutator)

    private class InactiveImpl: Impl {
        override fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
        ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
            throw IllegalStateException("Provider is in incorrect state.")

        override fun close() = Unit
    }

    private class ActiveImpl(
        private val connectionOps: DbConnectionOps
    ): Impl {
        override fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
        ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> {
            TODO("Not yet implemented")
        }

        override fun close() = Unit
    }
}