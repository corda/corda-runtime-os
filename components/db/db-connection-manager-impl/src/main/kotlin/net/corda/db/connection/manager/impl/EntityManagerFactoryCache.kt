package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.CordaDb
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.v5.base.util.contextLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * Read-through cache of [EntityManagerFactory] objects.
 */
class EntityManagerFactoryCache(
    private val dbConnectionsRepository: DbConnectionsRepository,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val allEntitiesSets: List<EntitiesSet>
) {
    companion object {
        private val logger = contextLogger()
    }

    // TODO - replace with caffeine cache
    private val cache = ConcurrentHashMap<UUID, EntityManagerFactory>()

    val clusterDbEntityManagerFactory: EntityManagerFactory by lazy {
        if(!dbConnectionsRepository.isInitialised)
            throw DBConfigurationException("Cluster DB must be initialised.")
        createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, dbConnectionsRepository.clusterDataSource)
    }

    fun getOrCreate(db: CordaDb): EntityManagerFactory {
        val entitiesSet =
            allEntitiesSets.singleOrNull { it.name == db.persistenceUnitName } ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreate(
            db.id ?: throw DBConfigurationException("Details for ${db.persistenceUnitName} cannot " +
                "be loaded because ID is missing") ,
            entitiesSet)
    }

    fun getOrCreate(id: UUID, entitiesSet: EntitiesSet): EntityManagerFactory {
        return cache.computeIfAbsent(id) {
            // TODO - use clusterDbEntityManagerFactory to load DB connection details from DB.
            logger.info("Loading DB connection details for ${entitiesSet.name}[$id]")
            throw NotImplementedError("TODO")
        }
    }

    private fun createManagerFactory(name: String, dataSource: DataSource): EntityManagerFactory {
        return entityManagerFactoryFactory.create(
            name,
            allEntitiesSets.single{it.name == name}.content.toList(),
            DbEntityManagerConfiguration(dataSource),
        )
    }
}