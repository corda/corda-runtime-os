package net.corda.db.testkit

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.LoggerFactory
import java.io.StringWriter
import javax.persistence.EntityManagerFactory

/**
 * Contains helper functions to create the database using [LiquibaseSchemaMigrator].
 *
 *  @ExtendWith(ServiceExtension::class)
 *  class PersistenceTests {
 *      companion object : DatabaseInstaller() {
 *          @InjectService(timeout = 5000)
 *          lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
 *          @InjectService(timeout = 5000)
 *          lateinit var lbm: LiquibaseSchemaMigrator
 *          private lateinit var cryptoEmf: EntityManagerFactory
 *          private lateinit var databaseInstaller: DatabaseInstaller
 *          @JvmStatic
 *          @BeforeAll
 *          fun setup() {
 *              databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
 *              cryptoEmf = databaseInstaller.setupDatabase(
 *                  cryptoDbConfig,
 *                  "crypto",
 *                  CordaDb.Crypto.persistenceUnitName,
 *                  CryptoEntities.classes.toList()
 *              )
 *          }
 *      }
 *      @Test
 *      fun whatever() {
 *      }
 *  }
 */
class DatabaseInstaller(
    private val factory: EntityManagerFactoryFactory,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val entitiesRegistry: JpaEntitiesRegistry
) {
    private val logger by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Creates the database.
     *
     * @param resourceSubPath the path segment in the 'net.corda:corda-db-schema' resources following
     * 'net.corda.db.schema', like 'crypto', 'config', etc.
     * @param persistenceUnitName the persistence unit name as defined in [CordaDb] enum, for the config database
     * you can use CordaDb.CordaCluster.persistenceUnitName.
     * @param entities list of entitles which tables should be created.
     * @return [EntityManagerFactory] that can be used to access the database.
     */
    fun setupDatabase(
        cfg: EntityManagerConfiguration,
        resourceSubPath: String,
        persistenceUnitName: String,
        entities: Set<Class<*>>
    ): EntityManagerFactory {
        val schemaClass = DbSchema::class.java
        logger.info("Creating schemas for ${cfg.dataSource.connection.metaData.url}".emphasise())
        val fullName = "${schemaClass.packageName}.$resourceSubPath"
        val resourcePrefix = fullName.replace('.', '/')
        val changeLogFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            fullName,
            listOf("$resourcePrefix/db.changelog-master.xml"),
            classLoader = schemaClass.classLoader
        )
        val changeLog = ClassloaderChangeLog(linkedSetOf(changeLogFiles))
        StringWriter().use { writer ->
            schemaMigrator.createUpdateSql(cfg.dataSource.connection, changeLog, writer)
            logger.info("Schema creation SQL: $writer")
        }
        schemaMigrator.updateDb(cfg.dataSource.connection, changeLog)
        logger.info("Create Entities".emphasise())
        val emf = factory.create(
            persistenceUnitName,
            entities.toList(),
            cfg
        )
        entitiesRegistry.register(persistenceUnitName, entities)
        return emf
    }
}