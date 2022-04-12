package net.corda.crypto.persistence.db.impl.signing

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SigningKeyCacheProvider::class])
class SigningKeyCacheProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : AbstractConfigurableComponent<SigningKeyCacheProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SigningKeyCacheProvider>(),
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
), SigningKeyCacheProvider {
    interface Impl: AutoCloseable {
        fun getInstance(): SigningKeyCache
    }

    override fun createInactiveImpl(): Impl =
        InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        ActiveImpl(event, dbConnectionManager, jpaEntitiesRegistry, keyEncodingService)

    override fun getInstance(): SigningKeyCache = impl.getInstance()

    class InactiveImpl : Impl {
        override fun getInstance(): SigningKeyCache =
            throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        private val dbConnectionOps: DbConnectionOps,
        private val jpaEntitiesRegistry: JpaEntitiesRegistry,
        private val keyEncodingService: KeyEncodingService
    ) : Impl {
        private val config: SmartConfig

        init {
            config = event.config[ConfigKeys.CRYPTO_CONFIG] ?:
                    SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        }

        private val instance by lazy(LazyThreadSafetyMode.PUBLICATION) {
            SigningKeyCacheImpl(
                config = config,
                dbConnectionOps = dbConnectionOps,
                jpaEntitiesRegistry = jpaEntitiesRegistry,
                keyEncodingService = keyEncodingService
            )
        }

        override fun getInstance(): SigningKeyCache = instance

        override fun close() = Unit
    }
}