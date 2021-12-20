package net.corda.libs.configuration.write.persistent.impl.dbutils

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.impl.CONFIG_DB_DRIVER
import net.corda.libs.configuration.write.persistent.impl.CONFIG_DB_PASS
import net.corda.libs.configuration.write.persistent.impl.CONFIG_DB_USER
import net.corda.libs.configuration.write.persistent.impl.CONFIG_JDBC_URL
import net.corda.libs.configuration.write.persistent.impl.MAX_POOL_SIZE
import net.corda.libs.configuration.write.persistent.impl.MIGRATION_FILE_LOCATION
import net.corda.libs.configuration.write.persistent.impl.PERSISTENCE_UNIT_NAME
import net.corda.libs.configuration.write.persistent.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.persistent.impl.entities.ConfigEntity
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * An implementation of [DBUtils].
 *
 * @param schemaMigrator The migrator for the liquibase scripts.
 * @param entityManagerFactoryFactory The factory for creating [EntityManagerFactory]s.
 * @property managedEntities The entities managed by the entity manager.
 * @property dataSource The data source for the cluster database.
 * @property entityManagerFactory The factory for creating [EntityManager]s.
 */
@Suppress("Unused")
@Component(service = [DBUtils::class])
internal class DBUtilsImpl @Activate constructor(
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : DBUtils {
    private var dataSource: DataSource? = null
    private var entityManagerFactory: EntityManagerFactory? = null

    override fun migrateClusterDatabase(config: SmartConfig) {
        val changeLogResourceFiles = managedEntities.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        val dataSource = dataSource ?: setDataSource(config)
        dataSource.connection.use { connection ->
            schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
        }
    }

    override fun writeEntities(config: SmartConfig, newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) {
        val entityManager = createEntityManager(config)

        try {
            entityManager.transaction.begin()
            entityManager.merge(newConfig)
            entityManager.persist(newConfigAudit)
            entityManager.transaction.commit()

        } finally {
            entityManager.close()
        }
    }

    override fun readConfigEntity(config: SmartConfig, section: String): ConfigEntity? {
        val entityManager = createEntityManager(config)

        return try {
            entityManager.find(ConfigEntity::class.java, section)
        } finally {
            entityManager.close()
        }
    }

    /** Uses the [entityManagerFactory] to create an [EntityManager]. */
    private fun createEntityManager(config: SmartConfig): EntityManager {
        val dataSource = dataSource ?: setDataSource(config)
        val entityManagerFactory = entityManagerFactory ?: setEntityManagerFactory(dataSource)
        return entityManagerFactory.createEntityManager()
    }

    /** Sets [dataSource] using the [config]. */
    private fun setDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return HikariDataSourceFactory()
            .create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
            .also { dataSource ->
                this.dataSource = dataSource
            }
    }

    /** Sets [entityManagerFactory] using the [dataSource]. */
    private fun setEntityManagerFactory(dataSource: DataSource): EntityManagerFactory {
        return entityManagerFactoryFactory
            .create(PERSISTENCE_UNIT_NAME, managedEntities, DbEntityManagerConfiguration(dataSource))
            .also { entityManagerFactory ->
                this.entityManagerFactory = entityManagerFactory
            }
    }
}