package net.corda.crypto.persistence.db.impl.hsm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMStoreProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMStoreProvider::class])
class HSMStoreProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : AbstractConfigurableComponent<HSMStoreProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMStoreProvider>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<DbConnectionManager>()
    ),
    setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), HSMStoreProvider {
    interface Impl: AutoCloseable {
        fun getInstance(): HSMStore
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(dbConnectionManager)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun getInstance(): HSMStore = impl.getInstance()

    class InactiveImpl : Impl {
        override fun getInstance(): HSMStore =
            throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        private val dbConnectionOps: DbConnectionOps
    ) : Impl {
        private val instance by lazy(LazyThreadSafetyMode.PUBLICATION) {
            HSMStoreImpl(
                entityManagerFactory = dbConnectionOps.getOrCreateEntityManagerFactory(
                    CordaDb.Crypto,
                    DbPrivilege.DML
                )
            )
        }

        override fun getInstance(): HSMStore = instance

        override fun close() = Unit
    }
}