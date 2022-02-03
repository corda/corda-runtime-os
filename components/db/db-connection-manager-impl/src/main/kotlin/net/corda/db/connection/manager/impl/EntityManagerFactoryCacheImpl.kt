package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.EntityManagerFactoryCache
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
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
    private val cache = ConcurrentHashMap<Pair<String,DbPrivilege>, EntityManagerFactory>()

    override val clusterDbEntityManagerFactory: EntityManagerFactory by lazy {
        createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, dbConnectionsRepository.clusterDataSource)
    }

    override fun getOrCreate(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        val entitiesSet =
            allEntitiesSets.singleOrNull { it.persistenceUnitName == db.persistenceUnitName } ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreate(
            db.persistenceUnitName,
            privilege,
            entitiesSet)
    }

    override fun getOrCreate(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet): EntityManagerFactory {
        return cache.computeIfAbsent(Pair(name,privilege)) {
            logger.info("Loading DB connection details for ${entitiesSet.persistenceUnitName}/$privilege")
            val ds = dbConnectionsRepository.get(name, privilege) ?:
                throw DBConfigurationException("Details for $name/$privilege cannot be found")
            createManagerFactory(name, ds)
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