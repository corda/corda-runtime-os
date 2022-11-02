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
import org.osgi.service.component.annotations.Component
import java.io.Writer
import java.sql.Connection
import java.util.UUID

@Component(service = [LiquibaseSchemaMigrator::class])
class LiquibaseSchemaMigratorImpl(
    private val liquibaseFactory: (
        changeLogFile: String,
        resourceAccessor: ResourceAccessor,
        database: Database
    ) -> Liquibase = ::Liquibase,
    private val databaseFactory: (connection: Connection) -> Database =
        { connection ->
            DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
) : LiquibaseSchemaMigrator {
    companion object {
        // default schema
        // NOTE: may need to become variable depending on the DB type
        const val DEFAULT_DB_SCHEMA = "PUBLIC"
        private val log = contextLogger()
    }

    override fun updateDb(datasource: Connection, dbChange: DbChange, tag: String?) {
        updateDb(datasource, dbChange, DEFAULT_DB_SCHEMA, tag)
    }

    override fun updateDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tag: String?) {
        process(datasource, dbChange, sql = null, controlTablesSchema, tag)
    }

    override fun rollBackDb(datasource: Connection, dbChange: DbChange, tagToRollbackTo: String) {
        rollBackDb(datasource, dbChange, DEFAULT_DB_SCHEMA, tagToRollbackTo)
    }

    override fun rollBackDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tagToRollbackTo: String) {
        processRollback(datasource, dbChange, controlTablesSchema, tagToRollbackTo)
    }

    /**
     * Create update [sql] for [datasource] based on [dbChange]
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param sql output
     */
    override fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer) {
        createUpdateSql(datasource, dbChange, DEFAULT_DB_SCHEMA, sql)
    }

    override fun createUpdateSql(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, sql: Writer) {
        process(datasource, dbChange, sql, controlTablesSchema)
    }

    private fun process(
        datasource: Connection,
        dbChange: DbChange,
        sql: Writer? = null,
        liquibaseSchemaName: String,
        tag: String? = null
    ) {
        val database = databaseFactory(datasource)

        // only set the schema if it's not specified as the default
        if (liquibaseSchemaName != DEFAULT_DB_SCHEMA) {
            log.info("Setting liquibaseSchemaName to $liquibaseSchemaName")
            database.liquibaseSchemaName = liquibaseSchemaName
        }

        // use UUID as we want to ensure this is unique and doesn't clash with a user defined changelog file.
        val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
        val lb = liquibaseFactory(
            masterChangeLogFileName,
            StreamResourceAccessor(masterChangeLogFileName, dbChange),
            database
        )

        log.info("Updating ${database.databaseProductName} ${database.databaseProductVersion} DB Schema for ${database.connection.catalog}")
        if (null == sql) {
            lb.update(Contexts())
        } else {
            lb.update(Contexts(), sql)
        }
        if (tag != null) {
            lb.tag(tag)
        }
        log.info("${database.connection.catalog} DB schema update complete")
    }

    private fun processRollback(
        datasource: Connection,
        dbChange: DbChange,
        liquibaseSchemaName: String,
        tagToRollbackTo: String
    ) {
        val database = databaseFactory(datasource)

        // only set the schema if it's not specified as the default
        if (liquibaseSchemaName != DEFAULT_DB_SCHEMA) {
            log.info("Setting liquibaseSchemaName to $liquibaseSchemaName")
            database.liquibaseSchemaName = liquibaseSchemaName
        }

        // use UUID as we want to ensure this is unique and doesn't clash with a user defined changelog file.
        val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
        val lb = liquibaseFactory(
            masterChangeLogFileName,
            StreamResourceAccessor(masterChangeLogFileName, dbChange),
            database
        )

        lb.rollback(tagToRollbackTo, Contexts())
        log.info("${database.connection.catalog} DB schema rollback complete")
    }
}
