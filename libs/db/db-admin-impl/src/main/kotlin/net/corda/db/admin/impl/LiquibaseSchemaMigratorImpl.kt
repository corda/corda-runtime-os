package net.corda.db.admin.impl

import liquibase.Contexts
import liquibase.GlobalConfiguration
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.DatabaseChangeLog
import liquibase.command.CommandScope
import liquibase.command.core.StatusCommandStep
import liquibase.command.core.TagCommandStep
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.ChangeExecListenerCommandStep
import liquibase.command.core.helpers.DatabaseChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.OfflineConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.LiquibaseException
import liquibase.io.WriterOutputStream
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Writer
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        },
    private val databaseFactoryOffline: (url: String, resourceAccessor: ResourceAccessor) -> Database =
        { url, resourceAccessor ->
            DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(OfflineConnection(url, resourceAccessor))
        },
    private val runChanges: Boolean = true
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

    override fun createUpdateSqlOffline(dbChange: DbChange, offlineDbDirPathString: String, sql: Writer) {
        processOffline(dbChange, offlineDbDirPathString, sql)
    }

    override fun listUnrunChangeSets(datasource: Connection, dbChange: DbChange): List<String> {
        liquibaseAccessLock.withLock {
            val database = databaseFactory(datasource)

            val masterChangeLogFileName = "master-changelog-${UUID.randomUUID()}.xml"

            return StatusCommandStep().listUnrunChangeSets(Contexts(), LabelExpression(),
                DatabaseChangeLog(masterChangeLogFileName), database).map { it.filePath }
        }
    }

    private fun process(
        datasource: Connection,
        dbChange: DbChange,
        sql: Writer? = null,
        liquibaseSchemaName: String,
        tag: String? = null
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
            val lb = liquibaseFactory(
                masterChangeLogFileName,
                StreamResourceAccessor(masterChangeLogFileName, dbChange),
                database
            )

            log.info(
                "Updating ${database.databaseProductName} ${database.databaseProductVersion} " +
                        "DB Schema for ${database.connection.catalog}"
            )

            try {
                val scopeObjects = mapOf(
                    Scope.Attr.database.name to lb.database,
                    Scope.Attr.resourceAccessor.name to lb.resourceAccessor
                )
                Scope.child(scopeObjects) {
                    val command = CommandScope(UpdateCommandStep.COMMAND_NAME[0])
                        .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, lb.database)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, lb.changeLogFile)
                        .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, Contexts().toString())
                        .addArgumentValue(UpdateCommandStep.LABEL_FILTER_ARG, LabelExpression().originalString)
                        .addArgumentValue(
                            ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG,
                            lb.defaultChangeExecListener
                        )
                        .addArgumentValue(
                            DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS,
                            lb.changeLogParameters
                        )
                        .addArgumentValue(TagCommandStep.TAG_ARG, tag)
                    if (sql != null) {
                        command.setOutput(
                            WriterOutputStream(
                                sql,
                                GlobalConfiguration.OUTPUT_FILE_ENCODING.currentValue
                            )
                        )
                    }
                    if (runChanges) {
                        command.execute()
                    }
                }
            } catch (e: Exception) {
                if (e is LiquibaseException) {
                    throw e
                } else {
                    throw LiquibaseException(e)
                }
            }
            log.info("${database.connection.catalog} DB schema update complete")
        }
    }

    private fun processOffline(
        dbChange: DbChange,
        offlineDbDirPathString: String,
        sql: Writer,
    ) {
        liquibaseAccessLock.withLock {
            val offlineChangeLogFileName = offlineDbDirPathString + "/changelog-${UUID.randomUUID()}.xml"
            val url = "offline:postgresql?changeLogFile=$offlineChangeLogFileName&outputLiquibaseSql=all"
            val database = databaseFactoryOffline(url, StreamResourceAccessor(offlineChangeLogFileName, dbChange))

            val lb = liquibaseFactory(
                offlineChangeLogFileName,
                StreamResourceAccessor(offlineChangeLogFileName, dbChange),
                database
            )

            log.info("Retrieving ${database.databaseProductName} DB Schema")
            lb.update(null, Contexts(), sql)

            File(offlineChangeLogFileName).delete()
        }
    }

    private fun processRollback(
        datasource: Connection,
        dbChange: DbChange,
        liquibaseSchemaName: String,
        tagToRollbackTo: String
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
            val lb = liquibaseFactory(
                masterChangeLogFileName,
                StreamResourceAccessor(masterChangeLogFileName, dbChange),
                database
            )

            lb.rollback(tagToRollbackTo, null, Contexts(), LabelExpression())
            log.info("${database.connection.catalog} DB schema rollback complete")
        }
    }
}
