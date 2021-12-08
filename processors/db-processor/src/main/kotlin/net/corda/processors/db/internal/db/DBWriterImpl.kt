package net.corda.processors.db.internal.db

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.processors.db.internal.config.ConfigEntity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [DBWriter::class])
class DBWriterImpl @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator
): DBWriter {

    private companion object {
        private const val JDBC_URL = "jdbc:postgresql://cluster-db:5432/cordacluster"
        private const val DB_USER = "user"
        private const val DB_PASSWORD = "pass"
        // TODO - Joel - Choose better persistence unit name.
        private const val PERSISTENCE_UNIT_NAME = "joel"
        private const val MIGRATION_FILE_LOCATION = "migration/db.changelog-master.xml"

        // TODO - Joel - Stop hardcoding this.
        private val MANAGED_ENTITIES = setOf(ConfigEntity::class.java)
    }

    private val dataSource = PostgresDataSourceFactory().create(JDBC_URL, DB_USER, DB_PASSWORD)
    private val entityManager = createEntityManager()

    init {
        // TODO - Joel - Introduce coordinator here to ensure database is ready before migrating.
        migrateDb()
    }

    override fun writeConfig(entity: Any) {
        entityManager.transaction.begin()
        entityManager.merge(entity)
        entityManager.transaction.commit()
    }

    private fun migrateDb() {
        val changeLogResourceFiles = MANAGED_ENTITIES.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
        schemaMigrator.updateDb(dataSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
    }

    private fun createEntityManager() = entityManagerFactoryFactory.create(
        PERSISTENCE_UNIT_NAME, listOf(ConfigEntity::class.java), DbEntityManagerConfiguration(dataSource)
    ).createEntityManager()
}