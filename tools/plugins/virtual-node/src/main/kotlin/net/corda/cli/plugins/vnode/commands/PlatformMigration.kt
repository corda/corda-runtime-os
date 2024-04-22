package net.corda.cli.plugins.vnode.commands

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.cli.plugins.vnode.withPluginClassLoader
import net.corda.sdk.vnode.VNodeDbSchemaGenerator
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
class PlatformMigration : Runnable {
    @CommandLine.Option(
        names = ["--jdbc-url"],
        description = [
            "JDBC Url of virtual node database/schema. Read access is required for Liquibase tracking tables " +
                "to determine what the current version of platform schemas each virtual node is currently at."
        ]
    )
    lateinit var jdbcUrl: String

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["Database username"]
    )
    lateinit var user: String

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

    data class PlatformMigrationConfig( // TODO used in the tests only, to be moved to SDK
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
        val generator = VNodeDbSchemaGenerator()
        val jdbcConnectionParams = VNodeDbSchemaGenerator.JdbcConnectionParams(jdbcUrl, user, password)

        withPluginClassLoader { // TODO or pass the classloader to the SDK?
            generator.generateVNodeMigrationSqlFile(holdingIdFilename, outputFilename, jdbcConnectionParams)
        }
    }
}
