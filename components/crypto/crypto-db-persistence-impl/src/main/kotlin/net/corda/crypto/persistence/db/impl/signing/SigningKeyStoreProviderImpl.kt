package net.corda.crypto.persistence.db.impl.signing

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.signingPersistence
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.signing.SigningKeyStoreProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [SigningKeyStoreProvider::class])
class SigningKeyStoreProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val vnodeInfo: VirtualNodeInfoReadService
) : AbstractConfigurableComponent<SigningKeyStoreProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SigningKeyStoreProvider>(),
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
), SigningKeyStoreProvider {
    interface Impl: AutoCloseable {
        fun getInstance(): SigningKeyStore
    }

    override fun createInactiveImpl(): Impl =
        InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(
        event,
        dbConnectionManager,
        jpaEntitiesRegistry,
        layeredPropertyMapFactory,
        keyEncodingService,
        vnodeInfo
    )

    override fun getInstance(): SigningKeyStore = impl.getInstance()

    class InactiveImpl : Impl {
        override fun getInstance(): SigningKeyStore =
            throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        private val dbConnectionOps: DbConnectionOps,
        private val jpaEntitiesRegistry: JpaEntitiesRegistry,
        private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
        private val keyEncodingService: KeyEncodingService,
        private val vnodeInfo: VirtualNodeInfoReadService
    ) : Impl {
        private val config: SmartConfig

        init {
            config = event.config.toCryptoConfig()
        }

        private val instance by lazy(LazyThreadSafetyMode.PUBLICATION) {
            SigningKeyStoreImpl(
                config = config.signingPersistence(),
                dbConnectionOps = dbConnectionOps,
                jpaEntitiesRegistry = jpaEntitiesRegistry,
                layeredPropertyMapFactory = layeredPropertyMapFactory,
                keyEncodingService = keyEncodingService,
                vnodeInfo = vnodeInfo
            )
        }

        override fun getInstance(): SigningKeyStore = instance

        override fun close() = Unit
    }
}