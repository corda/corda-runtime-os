package net.corda.crypto.persistence.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.CryptoConnectionsFactoryConfig
import net.corda.crypto.config.impl.cryptoConnectionFactory
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

@Component(service = [CryptoConnectionsFactory::class])
class CryptoConnectionsFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val vnodeInfo: VirtualNodeInfoReadService
) : AbstractConfigurableComponent<CryptoConnectionsFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.DefaultWithConfigReader(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        )
    ),
    configKeys = setOf(
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), CryptoConnectionsFactory {

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        logger,
        event.config.toCryptoConfig().cryptoConnectionFactory(),
        dbConnectionManager,
        jpaEntitiesRegistry,
        vnodeInfo
    )

    override fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
        impl.getEntityManagerFactory(tenantId)

    class Impl(
        private val logger: Logger,
        private val config: CryptoConnectionsFactoryConfig,
        private val dbConnectionOps: DbConnectionOps,
        private val jpaEntitiesRegistry: JpaEntitiesRegistry,
        private val vnodeInfo: VirtualNodeInfoReadService
    ) : DownstreamAlwaysUpAbstractImpl() {

        @Volatile
        private var connections: Cache<String, EntityManagerFactory> = createConnectionsCache()

        override fun onUpstreamRegistrationStatusChange(isUpstreamUp: Boolean, isDownstreamUp: Boolean?) {
            if (!isUpstreamUp) {
                connections = createConnectionsCache()
            }
        }

        override fun close() {
            super.close()
            val values = connections.asMap().values
            connections.invalidateAll()
            connections.cleanUp()
            values.forEach {
                try {
                    it.close()
                } catch (e: Throwable) {
                    // intentional
                }
            }
        }

        fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
            if (CryptoTenants.isClusterTenant(tenantId)) {
                dbConnectionOps.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
            } else {
                connections.get(tenantId) { createEntityManagerFactory(tenantId) }
            }

        private fun createEntityManagerFactory(tenantId: String) = dbConnectionOps.createEntityManagerFactory(
            connectionId = vnodeInfo.getByHoldingIdentityShortHash(ShortHash.of(tenantId))?.cryptoDmlConnectionId
                ?: throw throw IllegalStateException(
                    "virtual node for $tenantId is not registered."
                ),
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                ?: throw IllegalStateException(
                    "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                )
        )

        private fun createConnectionsCache(): Cache<String, EntityManagerFactory> {
            logger.info(
                "Building connections cache, maximumSize={}, expireAfterAccessMins={}",
                config.maximumSize,
                config.expireAfterAccessMins
            )
            return CacheFactoryImpl().build(
                "Crypto-Db-Connections-Cache",
                Caffeine.newBuilder()
                    .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(config.maximumSize)
                    .evictionListener { _, value, _ -> value?.close() })
        }
    }
}
