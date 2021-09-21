package net.corda.db.admin.impl

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.v5.base.util.contextLogger
import java.io.Writer
import java.sql.Connection
import java.util.UUID

class LiquibaseSchemaMigratorImpl(
    private val liquibaseFactory: (
        changeLogFile: String,
        resourceAccessor: ResourceAccessor,
        database: Database
    ) -> Liquibase = { changeLogFile, resourceAccessor, database ->
        Liquibase(changeLogFile, resourceAccessor, database)
    },
    private val databaseFactory: (connection: Connection) -> Database =
        { connection ->
            DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
) : LiquibaseSchemaMigrator {
    companion object {
        private val log = contextLogger()
    }

    override fun updateDb(datasource: Connection, dbChange: DbChange) {
        process(datasource, dbChange)
    }

    /**
     * Create update [sql] for [datasource] based on [changeLog]
     * does not update the DB
     *
     * @param datasource
     * @param changeLog
     * @param sql output
     */
    override fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer) {
        process(datasource, dbChange, sql)
    }

    private fun process(datasource: Connection, dbChange: DbChange, sql: Writer? = null) {
        val database = databaseFactory(datasource)
        // use UUID as we want to ensure this is unique and doesn't clash with a user defined changelog file.
        val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
        val lb = liquibaseFactory(
            masterChangeLogFileName,
            StreamResourceAccessor(masterChangeLogFileName, dbChange),
            database
        )

//        Thread.currentThread().contextClassLoader = LiquibaseSchemaMigratorImpl::class.java.classLoader
        log.info("Updating ${database.databaseProductName} ${database.databaseProductVersion} DB Schema for ${database.connection.catalog}")
        if (null == sql)
            lb.update(Contexts())
        else
            lb.update(Contexts(), sql)
        log.info("${database.connection.catalog} DB schema update complete")
    }
}
