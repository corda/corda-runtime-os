package net.corda.db.connection.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.datamodel.findDbConnectionByNameAndPrivilege
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * Repository for DB connections fetched from the Connections DB.
 *
 * Throws exception when trying to fetch a connection before the Cluster connection has been initialised.
 */
class DbConnectionsRepositoryImpl(
    private val clusterDataSource: DataSource,
    private val dataSourceFactory: DataSourceFactory,
    private val entityManagerFactory: EntityManagerFactory,
    private val dbConfigFactory: SmartConfigFactory
): DbConnectionsRepository {

    private companion object {
        private val logger = contextLogger()
    }

    override fun put(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String): UUID {

        return entityManagerFactory.createEntityManager().transaction {
            put(it, name, privilege, config, description, updateActor)
        }
    }

    override fun put(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String): UUID {
        logger.debug("Saving $privilege DB connection for $name: ${config.root().render()}")
        val configAsString = config.root().render(ConfigRenderOptions.concise())
        val existingConfig = entityManager.findDbConnectionByNameAndPrivilege(name, privilege)?.apply {
            update(configAsString, description, updateActor)
        } ?: DbConnectionConfig(
            UUID.randomUUID(),
            name,
            privilege,
            Instant.now(),
            updateActor,
            description,
            configAsString
        )
        entityManager.persist(existingConfig)
        entityManager.flush()
        return existingConfig.id
    }

    override fun get(name: String, privilege: DbPrivilege): DataSource? {
        if (name == CordaDb.CordaCluster.name) {
            return clusterDataSource
        }

        logger.debug("Fetching DB connection for $name")
        entityManagerFactory.createEntityManager().use {
            val dbConfig = it.findDbConnectionByNameAndPrivilege(name, privilege) ?:
                return null

            val config = ConfigFactory.parseString(dbConfig.config)
            logger.debug("Creating DB (${dbConfig.description}) from config: $config")
            return dataSourceFactory.createFromConfig(dbConfigFactory.create(config))
        }
    }

    override fun get(config: SmartConfig): DataSource {
        return dataSourceFactory.createFromConfig(dbConfigFactory.create(config))
    }

    override fun getClusterDataSource(): DataSource = clusterDataSource
}

