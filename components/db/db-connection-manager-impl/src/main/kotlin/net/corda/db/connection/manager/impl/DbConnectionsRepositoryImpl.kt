package net.corda.db.connection.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.DbConnectionAudit
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.datamodel.findDbConnectionByNameAndPrivilege
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Repository for DB connections fetched from the Connections DB.
 *
 * Throws exception when trying to fetch a connection before the Cluster connection has been initialised.
 */
class DbConnectionsRepositoryImpl(
    private val clusterDataSource: CloseableDataSource,
    private val dataSourceFactory: DataSourceFactory,
    private val entityManagerFactory: EntityManagerFactory,
    private val dbConfigFactory: SmartConfigFactory
): DbConnectionsRepository {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        val newDbConnection = DbConnectionConfig(
            UUID.randomUUID(),
            name,
            privilege,
            Instant.now(),
            updateActor,
            description,
            configAsString
        )
        val newDbConnectionAudit = DbConnectionAudit(newDbConnection)
        val existingConfig = entityManager.findDbConnectionByNameAndPrivilege(name, privilege)?.apply {
            update(configAsString, description, updateActor)
        } ?: newDbConnection

        entityManager.persist(existingConfig)
        entityManager.persist(newDbConnectionAudit)
        entityManager.flush()
        return existingConfig.id
    }

    override fun create(name: String, privilege: DbPrivilege): CloseableDataSource? {
        logger.debug("Fetching DB connection for $name")
        entityManagerFactory.createEntityManager().use {
            val dbConfig = it.findDbConnectionByNameAndPrivilege(name, privilege) ?:
                return null

            val config = ConfigFactory.parseString(dbConfig.config)
            logger.debug("Creating DB (${dbConfig.description}) from config: $config")
            return dataSourceFactory.createFromConfig(dbConfigFactory.create(config))
        }
    }

    override fun create(connectionId: UUID): CloseableDataSource? {
        logger.debug("Fetching DB connection for $connectionId")
        entityManagerFactory.createEntityManager().use {
            val dbConfig = it.find(DbConnectionConfig::class.java, connectionId)  ?:
            return null

            val config = ConfigFactory.parseString(dbConfig.config)
            logger.debug("Creating DB (${dbConfig.description}) from config: $config")
            return dataSourceFactory.createFromConfig(dbConfigFactory.create(config))
        }
    }

    override fun create(config: SmartConfig): CloseableDataSource {
        logger.debug("Creating CloseableDataSource from config: $config")
        return dataSourceFactory.createFromConfig(dbConfigFactory.create(config))
    }

    override fun getClusterDataSource(): CloseableDataSource = clusterDataSource
}

