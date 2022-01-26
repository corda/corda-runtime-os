package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.CordaDb
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.EntityManagerFactoryCache
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * Read-through cache of [EntityManagerFactory] objects.
 */
@Component(service = [EntityManagerFactoryCache::class])
class EntityManagerFactoryCacheImpl @Activate constructor(
    @Reference(service = DbConnectionsRepository::class)
    private val dbConnectionsRepository: DbConnectionsRepository,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = JpaEntitiesSet::class)
    private val allEntitiesSets: List<JpaEntitiesSet>,
): EntityManagerFactoryCache {
    companion object {
        private val logger = contextLogger()
    }

    // TODO - replace with caffeine cache
    private val cache = ConcurrentHashMap<UUID, EntityManagerFactory>()

    override val clusterDbEntityManagerFactory: EntityManagerFactory by lazy {
        createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, dbConnectionsRepository.clusterDataSource)
    }

    override fun getOrCreate(db: CordaDb): EntityManagerFactory {
        val entitiesSet =
            allEntitiesSets.singleOrNull { it.persistenceUnitName == db.persistenceUnitName } ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreate(
            db.id ?: throw DBConfigurationException("Details for ${db.persistenceUnitName} cannot " +
                "be loaded because ID is missing") ,
            entitiesSet)
    }

    override fun getOrCreate(id: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory {
        return cache.computeIfAbsent(id) {
            // TODO - use clusterDbEntityManagerFactory to load DB connection details from DB.
            logger.info("Loading DB connection details for ${entitiesSet.persistenceUnitName}[$id]")
            throw NotImplementedError("TODO")
        }
    }

    private fun createManagerFactory(name: String, dataSource: DataSource): EntityManagerFactory {
        return entityManagerFactoryFactory.create(
            name,
            allEntitiesSets.single{it.persistenceUnitName == name}.classes.toList(),
            DbEntityManagerConfiguration(dataSource),
        )
    }
}