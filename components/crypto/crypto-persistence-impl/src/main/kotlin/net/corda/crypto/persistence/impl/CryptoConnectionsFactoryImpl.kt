package net.corda.crypto.persistence.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactory
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
@Component(service = [CryptoConnectionsFactory::class])
class CryptoConnectionsFactoryImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cacheFactory: CacheFactory
) : CryptoConnectionsFactory {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = DbConnectionManager::class)
        dbConnectionManager: DbConnectionManager,
        @Reference(service = JpaEntitiesRegistry::class)
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService
    ) : this(
        coordinatorFactory,
        configurationReadService,
        dbConnectionManager,
        jpaEntitiesRegistry,
        virtualNodeInfoReadService,
        CacheFactoryImpl()
    )

    companion object {
        private val log = LoggerFactory.getLogger(CryptoConnectionsFactory::class.java)
    }

    private val dependentComponents =
        DependentComponents.of(
            ::dbConnectionManager,
            ::virtualNodeInfoReadService,
            ::configurationReadService
        )

    val coordinator =
        coordinatorFactory.createCoordinator<CryptoConnectionsFactory>(
            dependentComponents,
            ::processEvent
        )

    private var configRegistration: Resource? = null
    private var previousConfig: CryptoConnectionsFactoryCacheConfig? = null

    @Volatile
    @VisibleForTesting
    var connectionsCache: Cache<String, EntityManagerFactory>? = null

    override fun getEntityManagerFactory(tenantId: String): EntityManagerFactory  =
        if (CryptoTenants.isClusterTenant(tenantId)) {
            dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        } else {
            connectionsCache.let {
                requireNotNull(it) {
                    "${CryptoConnectionsFactoryImpl::connectionsCache::name} found null " +
                        "Current component state is ${coordinator::status}"
                }
                it.get(tenantId) { createEntityManagerFactory(tenantId) }
            }
        }

    private fun createEntityManagerFactory(tenantId: String) =
        dbConnectionManager.createEntityManagerFactory(
            connectionId = virtualNodeInfoReadService.getByHoldingIdentityShortHash(ShortHash.of(tenantId))?.cryptoDmlConnectionId
                ?: throw IllegalStateException(
                    "virtual node for $tenantId is not registered."
                ),
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                ?: throw IllegalStateException(
                    "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                )
        )

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Processing event: ${event::class.java.simpleName}" }
        when (event) {
            is StartEvent -> {}
            is StopEvent -> closeResources()

            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("All dependent components are UP, now registering for config updates")
                        configRegistration?.close()
                        configRegistration = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(
                                ConfigKeys.BOOT_CONFIG,
                                ConfigKeys.CRYPTO_CONFIG
                            )
                        )
                    }
                    LifecycleStatus.DOWN,
                    LifecycleStatus.ERROR -> {
                        log.info("Received ${event.status::name} event, closing resources")
                        updateStatus(event.status)
                        closeResources()
                    }
                }
            }
            is ConfigChangedEvent -> {
                val newConfig =
                    event.config.toCryptoConnectionsFactoryCacheConfig()
                if (newConfig != previousConfig) {
                    log.info(
                        "Received new configuration for cache, so creating new cache with "
                                + "maximumSize = ${newConfig.maximumSize}, expireAfterAccessMins = ${newConfig.expireAfterAccessMins}"
                    )
                    clearCache()
                    connectionsCache = cacheFactory.createConnectionsCache(newConfig)
                    previousConfig = newConfig
                }
                updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun updateStatus(newStatus: LifecycleStatus) {
        if (newStatus != coordinator.status) {
            coordinator.updateStatus(newStatus)
        }
    }

    private fun clearCache() {
        connectionsCache?.let { cache ->
            val values = cache.asMap().values
            cache.invalidateAll()
            cache.cleanUp()
            values.forEach {
                try {
                    it.close()
                } catch (e: Throwable) {
                    // intentional
                }
            }
        }
    }

    private fun closeResources() {
        configRegistration?.close()
        configRegistration = null
        clearCache()
        connectionsCache = null
        previousConfig = null
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}

private fun CacheFactory.createConnectionsCache(config: CryptoConnectionsFactoryCacheConfig): Cache<String, EntityManagerFactory> =
    build(
        "Crypto-Db-Connections-Cache",
        Caffeine.newBuilder()
            .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.maximumSize)
            .evictionListener { _, value, _ -> value?.close() })