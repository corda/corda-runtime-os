package net.corda.crypto.persistence.db.impl.hsm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.hsmPersistence
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.HSMCache
import net.corda.crypto.persistence.HSMCacheProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMCacheProvider::class])
class HSMCacheProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : AbstractConfigurableComponent<HSMCacheProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMCacheProvider>(),
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
), HSMCacheProvider {
    interface Impl: AutoCloseable {
        fun getInstance(): HSMCache
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(event, dbConnectionManager)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun getInstance(): HSMCache = impl.getInstance()

    class InactiveImpl : Impl {
        override fun getInstance(): HSMCache =
            throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        private val dbConnectionOps: DbConnectionOps
    ) : Impl {
        private val config: SmartConfig

        init {
            config = event.config.toCryptoConfig()
        }

        private val instance by lazy(LazyThreadSafetyMode.PUBLICATION) {
            HSMCacheImpl(
                config = config.hsmPersistence(),
                entityManagerFactory = dbConnectionOps.getOrCreateEntityManagerFactory(
                    CordaDb.Crypto,
                    DbPrivilege.DML
                )
            )
        }

        override fun getInstance(): HSMCache = instance

        override fun close() = Unit
    }
}