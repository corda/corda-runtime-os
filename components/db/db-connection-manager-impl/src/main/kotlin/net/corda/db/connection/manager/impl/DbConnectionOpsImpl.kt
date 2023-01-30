package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

class DbConnectionOpsImpl(
    private val dbConnectionsRepository: DbConnectionsRepository,
    private val entitiesRegistry: JpaEntitiesRegistry,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
    ): DbConnectionOps {

    private val clusterEntityManagerFactory = createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, getClusterDataSource())

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getClusterDataSource(): CloseableDataSource =
        dbConnectionsRepository.getClusterDataSource()

    override fun createDatasource(connectionId: UUID): CloseableDataSource =
        dbConnectionsRepository.create(connectionId) ?:
            throw DBConfigurationException("Details for $connectionId cannot be found")

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? =
        dbConnectionsRepository.create(name, privilege)

    override fun getDataSource(config: SmartConfig): CloseableDataSource =
        dbConnectionsRepository.create(config)

    override fun putConnection(name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID =
        dbConnectionsRepository.put(name, privilege, config, description, updateActor)

    override fun putConnection(entityManager: EntityManager, name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID =
        dbConnectionsRepository.put(entityManager, name, privilege, config, description, updateActor)

    override fun getClusterEntityManagerFactory(): EntityManagerFactory = clusterEntityManagerFactory

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        val entitiesSet =
            entitiesRegistry.get(db.persistenceUnitName) ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreateEntityManagerFactory(
            db.persistenceUnitName,
            privilege,
            entitiesSet)
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        logger.info("Loading DB connection details for ${entitiesSet.persistenceUnitName}/$privilege")
        val dataSource = dbConnectionsRepository.create(name, privilege) ?:
        throw DBConfigurationException("Details for $name/$privilege cannot be found")
        return entityManagerFactoryFactory.create(
            name,
            entitiesSet.classes.toList(),
            DbEntityManagerConfiguration(dataSource),
        )
    }

    override fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet):
            EntityManagerFactory {
        logger.info("Loading DB connection details for $connectionId")
        val dataSource = dbConnectionsRepository.create(connectionId) ?:
        throw DBConfigurationException("Details for $connectionId cannot be found")
        return entityManagerFactoryFactory.create(
            connectionId.toString(),
            entitiesSet.classes.toList(),
            DbEntityManagerConfiguration(dataSource),
        )
    }

    private fun createManagerFactory(name: String, dataSource: CloseableDataSource): EntityManagerFactory {
        return entityManagerFactoryFactory.create(
            name,
            entitiesRegistry.get(name)?.classes?.toList() ?:
            throw DBConfigurationException("Entity set for $name not found"),
            DbEntityManagerConfiguration(dataSource),
        )
    }
}