package net.corda.cli.plugins.vnode.commands

import liquibase.Contexts
import liquibase.GlobalConfiguration
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.UpdateSummaryEnum
import liquibase.UpdateSummaryOutputEnum
import liquibase.command.CommandScope
import liquibase.command.core.TagCommandStep
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.UpdateSqlCommandStep
import liquibase.command.core.helpers.ChangeExecListenerCommandStep
import liquibase.command.core.helpers.DatabaseChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.command.core.helpers.ShowSummaryArgument
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.io.WriterOutputStream
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.cli.plugins.vnode.withPluginClassLoader
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.sql.Connection
import java.sql.DriverManager

@CommandLine.Command(
    name = "platform-migration",
    description = [
        "Generates SQL commands to perform database schema migration of virtual nodes from one version of " +
            "Corda Platform Liquibase files to the next."
    ],
    mixinStandardHelpOptions = true
)
class PlatformMigration(
    private val config: PlatformMigrationConfig = PlatformMigrationConfig(),
    private val commandScopeFactory: (commandNames: Array<String>) -> CommandScope = { commandNames ->
        @Suppress("SpreadOperator")
        CommandScope(*commandNames)
    }
) : Runnable {
    @CommandLine.Option(
        names = ["--jdbc-url"],
        description = [
            "JDBC Url of virtual node database/schema. Read access is required for Liquibase tracking tables " +
                "to determine what the current version of platform schemas each virtual node is currently at."
        ]
    )
    var jdbcUrl: String? = null

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["Database username"]
    )
    var user: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["Database password. Defaults to no password."]
    )
    var password: String = ""

    @CommandLine.Option(
        names = ["-o", "--output-filename"],
        description = ["SQL output file. Default is '$SQL_FILENAME'"]
    )
    var outputFilename: String = SQL_FILENAME

    @CommandLine.Option(
        names = ["-i", "--input-filename"],
        description = [
            "File containing list of Virtual Node Short Holding Ids to migrate. File should contain one short" +
                "holding Id per line and nothing else. Default is '$HOLDING_ID_FILENAME'"
        ]
    )
    var holdingIdFilename: String = HOLDING_ID_FILENAME

    companion object {
        private const val HOLDING_ID_FILENAME = "./holdingIds"
        private const val SQL_FILENAME = "./vnodes.sql"
    }

    /**
     * Lazy because we don't want the list generated until run() is called, to ensure all the parameters are set
     */
    private val holdingIdsToMigrate: List<String> by lazy {
        mutableListOf<String>().apply {
            // Regex checks holdingId matches expected format and is one per line
            val regex = Regex("^[a-f0-9]{12}\$")
            config.lineReader(holdingIdFilename) {
                if (regex.matches(it)) {
                    add(it)
                } else if (it.isNotEmpty()) { // allow and ignore empty lines
                    throw IllegalArgumentException("Found invalid holding Id: $it")
                }
            }
        }
    }

    private data class LiquibaseFileAndSchema(val filename: String, val schemaPrefix: String)

    data class PlatformMigrationConfig(
        val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
        val lineReader: (String, (String) -> Unit) -> Unit = { filename, block ->
            File(filename).forEachLine { block(it) }
        },
        val liquibaseFactory: (String, Database) -> Liquibase = { file: String, database: Database ->
            Liquibase(file, ClassLoaderResourceAccessor(), database)
        },
        val jdbcConnectionFactory: (String?, String?, String?) -> Connection = { jdbcUrl, user, password ->
            DriverManager.getConnection(jdbcUrl, user, password)
        },
        val jdbcDatabaseFactory: (Connection) -> Database = { connection ->
            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
    )

    override fun run() {
        config.writerFactory(outputFilename).use { fileWriter ->
            listOf(
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-crypto/db.changelog-master.xml", "vnode_crypto_"),
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml", "vnode_uniq_"),
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-vault/db.changelog-master.xml", "vnode_vault_")
            ).forEach { fileAndSchema ->
                holdingIdsToMigrate.forEach { holdingId ->
                    generateSql(fileWriter, holdingId, fileAndSchema)
                }
            }
        }
    }

    private fun generateSql(fileWriter: FileWriter, holdingId: String, fileAndSchema: LiquibaseFileAndSchema) {
        withPluginClassLoader {
            val connection = config.jdbcConnectionFactory(jdbcUrl, user, password)
            val database = config.jdbcDatabaseFactory(connection).apply {
                val schemaName = fileAndSchema.schemaPrefix + holdingId
                defaultSchemaName = schemaName // our tables
                liquibaseSchemaName = schemaName // liquibase tracking tables
            }

            connection.use {
                val lb = config.liquibaseFactory(fileAndSchema.filename, database)
                val scopeObjects = mapOf(
                    Scope.Attr.resourceAccessor.name to lb.resourceAccessor
                )
                Scope.child(scopeObjects) {
                    commandScopeFactory(UpdateSqlCommandStep.COMMAND_NAME).configure(lb, null).also {
                        it.setOutput(
                            WriterOutputStream(
                                fileWriter,
                                GlobalConfiguration.OUTPUT_FILE_ENCODING.currentValue
                            )
                        )
                        it.execute()
                    }
                }
            }
        }
    }
}

private fun CommandScope.configure(lb: Liquibase, tag: String?): CommandScope {
    return this.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, lb.database)
        .addArgumentValue(UpdateCommandStep.CHANGELOG_ARG, lb.databaseChangeLog)
        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, lb.changeLogFile)
        .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, Contexts().toString())
        .addArgumentValue(UpdateCommandStep.LABEL_FILTER_ARG, LabelExpression().originalString)
        .addArgumentValue(
            ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG,
            lb.defaultChangeExecListener
        )
        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
        .addArgumentValue(
            DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS,
            lb.changeLogParameters
        )
        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY, UpdateSummaryEnum.SUMMARY)
        .addArgumentValue(TagCommandStep.TAG_ARG, tag)
}
