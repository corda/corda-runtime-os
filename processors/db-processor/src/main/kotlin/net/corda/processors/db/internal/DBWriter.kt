package net.corda.processors.db.internal

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory

class DBWriter(
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) {

    private companion object {
        const val JDBC_URL = "jdbc:postgresql://cluster-db:5432/cordacluster"
        const val DB_USER = "user"
        const val DB_PASSWORD = "pass"
    }

    private val dataSource = PostgresDataSourceFactory().create(JDBC_URL, DB_USER, DB_PASSWORD)
    private val entityManager = createEntityManager()

    init {
        migrateDb()
    }

    fun writeConfig(configEntity: ConfigEntity) {
        entityManager.transaction.begin()
        entityManager.merge(configEntity)
        entityManager.transaction.commit()
    }

    private fun migrateDb() {
        val changeLogResourceFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            ConfigEntity::class.java.packageName,
            listOf("migration/db.changelog-master.xml"),
            ConfigEntity::class.java.classLoader
        )
        val dbChange = ClassloaderChangeLog(linkedSetOf(changeLogResourceFiles))
        schemaMigrator.updateDb(dataSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
    }

    private fun createEntityManager() = entityManagerFactoryFactory.create(
        "joel",
        listOf(ConfigEntity::class.java),
        DbEntityManagerConfiguration(dataSource)
    ).createEntityManager()
}