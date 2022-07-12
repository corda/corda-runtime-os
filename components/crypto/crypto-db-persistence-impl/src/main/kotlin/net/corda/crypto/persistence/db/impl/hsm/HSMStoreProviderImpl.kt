package net.corda.crypto.persistence.db.impl.hsm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
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
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : AbstractConfigurableComponent<HSMStoreProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMStoreProvider>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    ),
    configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), HSMStoreProvider {
    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(coordinatorFactory, dbConnectionManager)

    override fun getInstance(): HSMStore = impl.getInstance()

    class Impl(
        coordinatorFactory: LifecycleCoordinatorFactory,
        private val dbConnectionOps: DbConnectionOps
    ) : AbstractImpl {
        private val instance by lazy(LazyThreadSafetyMode.PUBLICATION) {
            HSMStoreImpl(
                entityManagerFactory = dbConnectionOps.getOrCreateEntityManagerFactory(
                    CordaDb.Crypto,
                    DbPrivilege.DML
                )
            )
        }

        fun getInstance(): HSMStore = instance

        private val _downstream = DependenciesTracker.AlwaysUp(
            coordinatorFactory,
            this
        ).also { it.start() }

        override val downstream: DependenciesTracker = _downstream

        override fun close() {
            _downstream.close()
        }
    }
}