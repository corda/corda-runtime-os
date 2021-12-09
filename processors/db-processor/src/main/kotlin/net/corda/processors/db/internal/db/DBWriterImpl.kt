package net.corda.processors.db.internal.db

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.processors.db.internal.config.writer.ConfigEntity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.sql.DataSource

/** An implementation of [DBWriter]. */
@Suppress("Unused")
@Component(service = [DBWriter::class])
class DBWriterImpl @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator
) : DBWriter {

    private companion object {
        // TODO - Joel - Stop hardcoding this.
        private val MANAGED_ENTITIES = listOf(ConfigEntity::class.java)
    }

    private val dataSource = createDataSource()
    private val entityManager = createEntityManager()

    init {
        // TODO - Joel - Introduce coordinator here to ensure database is ready before migrating.
        migrateDb()
    }

    override fun writeEntity(entities: Iterable<Any>) {
        entityManager.transaction.begin()
        entities.forEach { entity ->
            entityManager.merge(entity)
        }
        entityManager.transaction.commit()
    }

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(): DataSource {
        // TODO - Joel - End hardcoding of username and password. Pass them down in config.
        val username = DB_USER
        val password = DB_PASSWORD
        // TODO - Joel - Do not hardcode use of Postgres.
        return PostgresDataSourceFactory().create(JDBC_URL, username, password)
    }

    // TODO - Joel - Understand this better. Can I just use a single entity manager for the lifetime of this component?
    /** Creates an entity manager for the [MANAGED_ENTITIES] and cluster database. */
    private fun createEntityManager() = entityManagerFactoryFactory.create(
        PERSISTENCE_UNIT_NAME, MANAGED_ENTITIES, DbEntityManagerConfiguration(dataSource)
    ).createEntityManager()

    // TODO - Joel - Move this migration to its proper place.
    /** Applies the Liquibase schema migrations for the [MANAGED_ENTITIES]. */
    private fun migrateDb() {
        // TODO - Joel - This is using `impl` classes. Check this is correct.
        val changeLogResourceFiles = MANAGED_ENTITIES.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
        schemaMigrator.updateDb(dataSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
    }
}