package net.corda.cli.plugins.dbconfig

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.OfflineConnection
import liquibase.database.core.PostgresDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.nio.file.Files.deleteIfExists
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager


@CommandLine.Command(
    name = "spec",
    description = ["Does database schema generation from liquibase. Can run offline or connect to a live database for " +
            "migration to a new version."]
)
class Spec(private val config: SpecConfig = SpecConfig()) : Runnable {
    @CommandLine.Option(
        names = ["--change-log"],
        description = ["Path and filename of the databasechangelog CSV file which is created by Liquibase in offline" +
                "mode. Defaults to '$DEFAULT_CHANGELOG_PATH'"]
    )
    var databaseChangeLogFile = Path.of(DEFAULT_CHANGELOG_PATH)

    @CommandLine.Option(
        names = ["-c", "--clear-change-log"],
        description = ["Automatically delete the changelogCSV to force generation of the sql files"]
    )
    var clearChangeLog: Boolean? = false

    @CommandLine.Option(
        names = ["-s", "--schemas"],
        description = ["List of sql files to generate. Default is files for all schemas. Options are: $DEFAULT_SCHEMAS"],
        split = ","
    )
    var schemasToGenerate: List<String> = DEFAULT_SCHEMA_OPTIONS

    @CommandLine.Option(
        names = ["-g", "--generate-schema-sql"],
        description = ["By default sql files generated are schema-less, it is the responsibility of the db admin to apply " +
                "these files to the correct schema. This option adds schema creation to the sql files instead. The schema " +
                "names should be passed as a list, where each item takes the form 'schema-type:schema-name'. Schema-types " +
                "are taken from: $SCHEMA_OPTIONS. E.g \"config:my-config-schema,crypto:my-crypto-schema\" where config tables " +
                "would end up in a schema called my-config-schema and crypto tables would end up in a schema called " +
                "my-crypto-schema. Any schemas not specified will take the default name, which is the same as schema-type. To " +
                "generate schemas using all default names pass \"\" as the value."],
        split = ","
    )
    var generateSchemaSql: List<String>? = null

    @CommandLine.Option(
        names = ["-g", "--generate-schema-sql"],
        description = ["By default sql files generated are schemaless, it is the responsibility of the db admin to apply " +
                "these files to the correct schema themselves. Specifying this option will add schema creation to each" +
                "of the sql files. The schemas generated will be the Corda defaults: $DEFAULT_SCHEMAS"]
    )
    var generateSchemaSql: Boolean? = false

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputDir: String = "."

    @CommandLine.Option(
        names = ["--jdbc-url"],
        description = ["JDBC Url of database. If not specified runs in offline mode"]
    )
    var jdbcUrl: String? = null

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["Database username"]
    )
    var user: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["Database password"]
    )
    var password: String? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)

        private const val DEFAULT_SCHEMAS = "config, messagebus, rbac, crypto"
        private const val DEFAULT_CHANGELOG_PATH = "./databasechangelog.csv"
    }

    data class SpecConfig(
        val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
        val liquibaseFactory: (String, Database) -> Liquibase =
            { file: String, database: Database -> Liquibase(file, ClassLoaderResourceAccessor(), database) },
        val deleteFile: (Path) -> Unit = { path -> deleteIfExists(path) },
        val jdbcConnectionFactory: (String?, String?, String?) -> Connection = { jdbcUrl, user, password ->
            DriverManager.getConnection(
                jdbcUrl,
                user,
                password
            )
        },
        val jdbcDatabaseFactory: (Connection) -> Database = { connection ->
            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
    )

    override fun run() {
        if (clearChangeLog == true) {
            config.deleteFile(databaseChangeLogFile)
        }

        listOf(
            "net/corda/db/schema/config/db.changelog-master.xml",
            "net/corda/db/schema/messagebus/db.changelog-master.xml",
            "net/corda/db/schema/rbac/db.changelog-master.xml",
            "net/corda/db/schema/crypto/db.changelog-master.xml"
        ).filterOnSchemasToGenerate().also { logger.info("Using the following schemas $it") }.forEach(::generateSql)
    }

    private fun List<String>.filterOnSchemasToGenerate() =
        if (schemasToGenerate.isEmpty()) this else this.filter { file ->
            schemasToGenerate.any { schemaName ->
                file.contains(schemaName)
            }
        }.toMap()

    private fun generateSql(filename: String) {
        // Grabs dirname above db.changelog-master.xml to derive the package
        val test = "([a-zA-Z0-9]+)/db\\.changelog-master\\.xml".toRegex()
        // Make .sql output file
        val schemaName = checkNotNull(test.find(filename)).groupValues.last()
        val outputFileName = "${outputDir.removeSuffix("/")}/${schemaName}.sql"

        // This is a workaround to make liquibase play nicely with the logger that's on the class loader
        val oldCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = DatabaseBootstrapAndUpgrade.classLoader

        config.writerFactory(outputFileName).use { outputFile ->
            writeSchemaToFile(schemaName, outputFile, filename)
        }

        Thread.currentThread().contextClassLoader = oldCl
    }

    private fun writeSchemaToFile(
        schemaName: String,
        outputFile: FileWriter,
        filename: String
    ) {
        // A curious feature of the liquibase connection is that if you attempt to generate multiple sql files against
        // the same one, only the first one ends up generating offline sql when using offline mode. Note that multiple
        // connections don't overwrite any previous databasechangelog.csv, it is still appended to on each invocation.
        val (connection, database) = connectionAndDatabase()
        connection.use {
            if (generateSchemaSql == true) {
                // Our Liquibase files contain no schema information deliberately. Each db.changelog-master.xml
                // represents an isolated data set which could be put into its own database and therefore be separately
                // permissioned. If requested this tool will:
                // 1) Specify a schema for the current file in order that the tables get created under that schema
                // 2) Ensure liquibase uses that schema for any tracking tables
                // 3) Adds SQL to the output file to create the schema if it doesn't exist

                database.defaultSchemaName = schemaName // our tables
                database.liquibaseSchemaName = schemaName // liquibase tracking tables
                outputFile.write(System.lineSeparator())
                outputFile.write("CREATE SCHEMA IF NOT EXISTS ${schemaName};")
                outputFile.write(System.lineSeparator())
                outputFile.write(System.lineSeparator())
            }

            config.liquibaseFactory(filename, database).update(Contexts(), outputFile)
        }
    }

    private fun connectionAndDatabase() = if (jdbcUrl == null) {
        val database = PostgresDatabase()
        val connection = OfflineConnection("offline:postgresql?changeLogFile=$databaseChangeLogFile", ClassLoaderResourceAccessor())
        database.connection = connection
        connection.attached(database)
        Pair(connection, database)
    } else {
        val connection = config.jdbcConnectionFactory(jdbcUrl, user, password)
        val database = config.jdbcDatabaseFactory(connection)
        Pair(connection, database)
    }
}
