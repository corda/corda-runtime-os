package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.CordaDb
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.model.RpcRbacEntitiesSet
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID
import javax.persistence.EntityManagerFactory

@Component(service = [DbConnectionManager::class])
class DbConnectionManagerImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = EntityManagerFactoryFactory::class)
    entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val dbConnectionsRepository: DbConnectionsRepository = DbConnectionsRepository(),
    private val cache: EntityManagerFactoryCache = EntityManagerFactoryCache(
        dbConnectionsRepository, entityManagerFactoryFactory, allEntitySets)
): DbConnectionManager {
    companion object {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        // TODO - review this pattern. It's different from the initial RBAC implementation that it
        //  doesn't use OSGi injection. Reasons for change:
        //    - These lists are known at compile time
        //    - We should be able to combine entities from multiple modules (e.g. configuration + vnode)
        //  Maybe this list needs to be defined in the DB Processor module instead to decouple things?
        val allEntitySets = listOf(
            // TODO - add VNode entities, for example.
            EntitiesSet.create(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes),
            // TODO - refactor RpcRbacEntitiesSet
            EntitiesSet.create(CordaDb.RBAC.persistenceUnitName, RpcRbacEntitiesSet().content)
        )
    }

    private val eventHandler = DbConnectionManagerEventHandler(dbConnectionsRepository)
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<DbConnectionManager>(eventHandler)

    override fun getOrCreateEntityManagerFactory(db: CordaDb): EntityManagerFactory =
        cache.getOrCreate(db)

    override fun getOrCreateEntityManagerFactory(connectionID: UUID, entitiesSet: EntitiesSet): EntityManagerFactory =
        cache.getOrCreate(connectionID, entitiesSet)

    override fun putConnection(connectionID: UUID, config: SmartConfig) {
        dbConnectionsRepository.put(connectionID, config)
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