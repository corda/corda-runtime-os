package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.EntityManagerFactoryCache
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesSet
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [DbConnectionManager::class])
class DbConnectionManagerImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionsRepository::class)
    private val dbConnectionsRepository: DbConnectionsRepository,
    @Reference(service = EntityManagerFactoryCache::class)
    private val cache: EntityManagerFactoryCache,
): DbConnectionManager {
    private val eventHandler = DbConnectionManagerEventHandler(dbConnectionsRepository)
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<DbConnectionManager>(eventHandler)

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory =
        cache.getOrCreate(db, privilege)

    override fun getOrCreateEntityManagerFactory(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet):
            EntityManagerFactory =
        cache.getOrCreate(name, privilege, entitiesSet)

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String) {
        dbConnectionsRepository.put(name, privilege, config, description, updateActor)
    }

    override val clusterDbEntityManagerFactory: EntityManagerFactory
        get() = cache.clusterDbEntityManagerFactory

    override fun bootstrap(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    override fun close() {
        lifecycleCoordinator.close()
    }
}