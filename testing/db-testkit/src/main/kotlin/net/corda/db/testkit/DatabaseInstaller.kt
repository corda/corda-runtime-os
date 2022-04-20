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
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

/**
 * Contains helper functions to create the database using [LiquibaseSchemaMigrator].
 * It has to be subclassed and the subclass must provide two properties (with any name) with the types of
 * [EntityManagerFactoryFactory], [JpaEntitiesRegistry], and [LiquibaseSchemaMigrator].
 *
 *  @ExtendWith(ServiceExtension::class)
 *  class PersistenceTests {
 *      companion object : DatabaseInstaller() {
 *          @InjectService(timeout = 5000)
 *          lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
 *          @InjectService(timeout = 5000)
 *          lateinit var lbm: LiquibaseSchemaMigrator
 *          private lateinit var cryptoEmf: EntityManagerFactory
 *          @JvmStatic
 *          @BeforeAll
 *          fun setup() {
 *              cryptoEmf = cryptoDbConfig.setupDatabase(
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
abstract class DatabaseInstaller {
    protected val logger by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Using reflection due that the InjectService annotation doesn't discover the properties defined on
     * a super class (at least for the companion)
     */
    private val _emff: EntityManagerFactoryFactory by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val type = EntityManagerFactoryFactory::class.createType()
        this::class.memberProperties.firstOrNull {
            it.returnType == type && it != ::_emff
        }?.getter?.call(this) as? EntityManagerFactoryFactory
            ?: throw java.lang.IllegalStateException(
                "The child class doesn't have ${EntityManagerFactoryFactory::class.java.name} property"
            )
    }

    private val _lbm: LiquibaseSchemaMigrator by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val type = LiquibaseSchemaMigrator::class.createType()
        this::class.memberProperties.firstOrNull {
            it.returnType == type && it != ::_lbm
        }?.getter?.call(this) as? LiquibaseSchemaMigrator
            ?: throw java.lang.IllegalStateException(
                "The child class doesn't have ${LiquibaseSchemaMigrator::class.java.name} property"
            )
    }

    private val _er: JpaEntitiesRegistry by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val type = JpaEntitiesRegistry::class.createType()
        this::class.memberProperties.firstOrNull {
            it.returnType == type && it != ::_lbm
        }?.getter?.call(this) as? JpaEntitiesRegistry
            ?: throw java.lang.IllegalStateException(
                "The child class doesn't have ${JpaEntitiesRegistry::class.java.name} property"
            )
    }

    /**
     * Creates the database.
     *
     * @param resourceSubPath the path segment in the 'net.corda:corda-db-schema' resources following
     * 'net.corda.db.schema', like 'crypto', 'config', etc.
     * @param persistenceUnitName the persistence unit name as defined in [CordaDb] enum, for the config database
     * you can use CordaDb.CordaCluster.persistenceUnitName.
     * @param entities list of entitles which tables should be created.
     */
    fun EntityManagerConfiguration.setupDatabase(
        resourceSubPath: String,
        persistenceUnitName: String,
        entities: Set<Class<*>>
    ): EntityManagerFactory {
        val schemaClass = DbSchema::class.java
        logger.info("Creating schemas for ${this.dataSource.connection.metaData.url}".emphasise())
        val fullName = "${schemaClass.packageName}.$resourceSubPath"
        val resourcePrefix = fullName.replace('.', '/')
        val changeLogFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            fullName,
            listOf("$resourcePrefix/db.changelog-master.xml"),
            classLoader = schemaClass.classLoader
        )
        val changeLog = ClassloaderChangeLog(linkedSetOf(changeLogFiles))
        StringWriter().use { writer ->
            _lbm.createUpdateSql(this.dataSource.connection, changeLog, writer)
            logger.info("Schema creation SQL: $writer")
        }
        _lbm.updateDb(this.dataSource.connection, changeLog)
        logger.info("Create Entities".emphasise())
        val emf = _emff.create(
            persistenceUnitName,
            entities.toList(),
            this
        )
        _er.register(persistenceUnitName, entities)
        return emf
    }
}