package net.corda.libs.configuration.write.impl.dbutils

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.HikariDataSourceFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.impl.CONFIG_DB_DRIVER
import net.corda.libs.configuration.write.impl.CONFIG_DB_PASS
import net.corda.libs.configuration.write.impl.CONFIG_DB_USER
import net.corda.libs.configuration.write.impl.CONFIG_JDBC_URL
import net.corda.libs.configuration.write.impl.MAX_POOL_SIZE
import net.corda.libs.configuration.write.impl.MIGRATION_FILE_LOCATION
import net.corda.libs.configuration.write.impl.PERSISTENCE_UNIT_NAME
import net.corda.libs.configuration.write.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.impl.entities.ConfigEntity
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.utils.transaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/** An implementation of [DBUtils]. */
@Suppress("Unused")
@Component(service = [DBUtils::class])
internal class DBUtilsImpl @Activate constructor(
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : DBUtils {

    private val managedEntities: List<Class<out Any>>
        get() = listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java)
    private var dataSource: DataSource? = null
    private var entityManagerFactory: EntityManagerFactory? = null

    override fun migrateClusterDatabase(config: SmartConfig) {
        val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
            ChangeLogResourceFiles(klass.packageName, listOf(MIGRATION_FILE_LOCATION), klass.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        val dataSource = dataSource ?: setDataSource(config)
        dataSource.connection.use { connection ->
            schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
        }
    }

    override fun writeEntities(
        config: SmartConfig,
        entitiesToMerge: Collection<Any>,
        entitiesToPersist: Collection<Any>
    ) = createEntityManager(config).transaction { entityManager ->
        entitiesToMerge.forEach { entity -> entityManager.merge(entity) }
        entitiesToPersist.forEach { entity -> entityManager.persist(entity) }
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