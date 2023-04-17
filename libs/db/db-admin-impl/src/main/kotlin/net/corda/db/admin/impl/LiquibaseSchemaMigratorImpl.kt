package net.corda.db.admin.impl

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.command.CommandScope
import liquibase.command.core.RollbackCommandStep
import liquibase.command.core.TagCommandStep
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.ChangeExecListenerCommandStep
import liquibase.command.core.helpers.DatabaseChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.Writer
import java.sql.Connection
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [LiquibaseSchemaMigrator::class])
class LiquibaseSchemaMigratorImpl(
    private val liquibaseFactory: (
        changeLogFile: String,
        resourceAccessor: ResourceAccessor,
        database: Database,
    ) -> Liquibase = ::Liquibase,
    private val databaseFactory: (connection: Connection) -> Database =
        { connection ->
            DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
        },
    private val commandScopeFactory: (commandNames: Array<String>) -> CommandScope = { commandNames ->
        @Suppress("SpreadOperator")
        CommandScope(*commandNames)
    }
) : LiquibaseSchemaMigrator {
    companion object {
        // default schema
        // NOTE: may need to become variable depending on the DB type
        const val DEFAULT_DB_SCHEMA = "PUBLIC"
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val liquibaseAccessLock = ReentrantLock()
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

    override fun listUnrunChangeSets(datasource: Connection, dbChange: DbChange): List<String> {
        liquibaseAccessLock.withLock {
            val database = databaseFactory(datasource)

            val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
            val liquibase = liquibaseFactory(
                masterChangeLogFileName,
                StreamResourceAccessor(masterChangeLogFileName, dbChange),
                database
            )

            return liquibase.listUnrunChangeSets(Contexts(), LabelExpression()).map { it.filePath }
        }
    }

    private fun process(
        datasource: Connection,
        dbChange: DbChange,
        sql: Writer? = null,
        liquibaseSchemaName: String,
        tag: String? = null,
    ) {
        liquibaseAccessLock.withLock {
            val database = databaseFactory(datasource)

            // only set the schema if it's not specified as the default
            if (liquibaseSchemaName != DEFAULT_DB_SCHEMA) {
                log.info("Setting liquibaseSchemaName to $liquibaseSchemaName")
                database.liquibaseSchemaName = liquibaseSchemaName
            }

            // use UUID as we want to ensure this is unique and doesn't clash with a user defined changelog file.
            val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
            liquibaseFactory(
                masterChangeLogFileName,
                StreamResourceAccessor(masterChangeLogFileName, dbChange),
                database
            ).use { lb ->
                log.info(
                    "Updating ${database.databaseProductName} ${database.databaseProductVersion} " +
                            "DB Schema for ${database.connection.catalog}"
                )
                if (null == sql) {
                    val contexts = Contexts()
                    val labelExpression = LabelExpression()
                    commandScopeFactory(UpdateCommandStep.COMMAND_NAME)
                        .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, lb.database)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, lb.changeLogFile)
                        .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, contexts.toString())
                        .addArgumentValue(UpdateCommandStep.LABEL_FILTER_ARG, labelExpression.originalString)
                        .addArgumentValue(ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG,
                            lb.defaultChangeExecListener)
                        .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS, lb.changeLogParameters)
                        .execute()
                } else {
                    lb.update(Contexts(), sql)
                }
                if (tag != null) {
                    commandScopeFactory(arrayOf("tag"))
                        .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, database)
                        .addArgumentValue(TagCommandStep.TAG_ARG, tag)
                        .execute()
                }
                log.info("${database.connection.catalog} DB schema update complete")
            }
        }
    }

    private fun processRollback(
        datasource: Connection,
        dbChange: DbChange,
        liquibaseSchemaName: String,
        tagToRollbackTo: String,
    ) {
        liquibaseAccessLock.withLock {
            val database = databaseFactory(datasource)

            // only set the schema if it's not specified as the default
            if (liquibaseSchemaName != DEFAULT_DB_SCHEMA) {
                log.info("Setting liquibaseSchemaName to $liquibaseSchemaName")
                database.liquibaseSchemaName = liquibaseSchemaName
            }

            // use UUID as we want to ensure this is unique and doesn't clash with a user defined changelog file.
            val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"
            liquibaseFactory(
                masterChangeLogFileName,
                StreamResourceAccessor(masterChangeLogFileName, dbChange),
                database
            ).use { lb ->
                val contexts = Contexts()
                val labelExpression = LabelExpression()
                commandScopeFactory(RollbackCommandStep.COMMAND_NAME)
                    .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, lb.database)
                    .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_FILE_ARG, null)
                    .addArgumentValue(DatabaseChangelogCommandStep.CONTEXTS_ARG, contexts.toString())
                    .addArgumentValue(DatabaseChangelogCommandStep.LABEL_FILTER_ARG, labelExpression.originalString)
                    .addArgumentValue(ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG, lb.defaultChangeExecListener)
                    .addArgumentValue(RollbackCommandStep.TAG_ARG, tagToRollbackTo)
                    .addArgumentValue(RollbackCommandStep.ROLLBACK_SCRIPT_ARG, null)
                    .execute()
                log.info("${database.connection.catalog} DB schema rollback complete")
            }
        }
    }
}
