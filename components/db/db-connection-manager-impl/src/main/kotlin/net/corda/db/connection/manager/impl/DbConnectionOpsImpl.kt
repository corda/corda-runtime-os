package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.util.contextLogger
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

class DbConnectionOpsImpl(
    private val dbConnectionsRepository: DbConnectionsRepository,
    private val entitiesRegistry: JpaEntitiesRegistry,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
    ): DbConnectionOps {

    private val clusterEntityManagerFactory = createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, getClusterDataSource())

    companion object {
        private val logger = contextLogger()
    }

    override fun getClusterDataSource(): DataSource =
        dbConnectionsRepository.getClusterDataSource()

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? =
        dbConnectionsRepository.get(name, privilege)

    override fun putConnection(name: String, privilege: DbPrivilege, config: SmartConfig, description: String?, updateActor: String): UUID =
        dbConnectionsRepository.put(name, privilege, config, description, updateActor)

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
        val ds = dbConnectionsRepository.get(name, privilege) ?:
        throw DBConfigurationException("Details for $name/$privilege cannot be found")
        return createManagerFactory(name, ds)
    }

    private fun createManagerFactory(name: String, dataSource: DataSource): EntityManagerFactory {
        return entityManagerFactoryFactory.create(
            name,
            entitiesRegistry.get(name)?.classes?.toList() ?:
            throw DBConfigurationException("Entity set for $name not found"),
            DbEntityManagerConfiguration(dataSource),
        )
    }
}