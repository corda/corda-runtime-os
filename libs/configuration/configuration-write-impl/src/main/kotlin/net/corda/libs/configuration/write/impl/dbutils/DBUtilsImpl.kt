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
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/** An implementation of [DBUtils]. */
internal class DBUtilsImpl(
    config: SmartConfig,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) : DBUtils {

    private val managedEntities = listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java)
    private val dataSource = createDataSource(config)
    private val entityManagerFactory = createEntityManagerFactory()

    override fun migrateClusterDatabase() {
        val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
            ChangeLogResourceFiles(klass.packageName, listOf(MIGRATION_FILE_LOCATION), klass.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        dataSource.connection.use { connection ->
            schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
        }
    }

    override fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) =
        entityManagerFactory.createEntityManager().transaction { entityManager ->
            entityManager.merge(newConfig)
            entityManager.persist(newConfigAudit)
        }

    override fun readConfigEntity(section: String): ConfigEntity? {
        val entityManager = entityManagerFactory.createEntityManager()

        return try {
            entityManager.find(ConfigEntity::class.java, section)
        } finally {
            entityManager.close()
        }
    }

    /** Creates a [DataSource] using the [config]. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return HikariDataSourceFactory()
            .create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Creates an [EntityManagerFactory] using the [dataSource]. */
    private fun createEntityManagerFactory() = entityManagerFactoryFactory
        .create(PERSISTENCE_UNIT_NAME, managedEntities, DbEntityManagerConfiguration(dataSource))
}