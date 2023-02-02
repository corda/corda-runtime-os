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
import java.sql.DriverManager


@CommandLine.Command(name = "spec", description = ["Does database schema generation from liquibase"])
class Spec(
    private val databaseChangeLogFile: Path = Path.of("./databasechangelog.csv"),
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
    private val liquibaseFactory: (String, Database) -> Liquibase =
        { file: String, database: Database -> Liquibase(file, ClassLoaderResourceAccessor(), database) },
    private val deleteFile: (Path) -> Unit = { path -> deleteIfExists(path) }
) : Runnable {
    @CommandLine.Option(
        names = ["-c", "--clear-change-log"],
        description = ["Automatically delete the changelogCSV in the PWD to force generation of the sql files"]
    )
    var clearChangeLog: Boolean? = false

    @CommandLine.Option(
        names = ["-s", "--schemas"],
        description = ["File of schema files to generate. Default is all schemas"],
        split = ","
    )
    var schemasToGenerate: List<String> = emptyList<String>()

    @CommandLine.Option(
        names = ["-i", "--ignore-schema-sql"],
        description = ["By default sql files include a command to create a schema in the database for the tables being" +
                "generated. This option allows the skipping of the generation of that command in order the SQL can be" +
                "applied to a database of its own."]
    )
    var ignoreSchemaSql: Boolean? = false

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
    }

    override fun run() {
        if (clearChangeLog == true) {
            deleteFile(databaseChangeLogFile)
        }

        mapOf(
            "net/corda/db/schema/config/db.changelog-master.xml" to DbMetadata(),
            // messagebus is forced into the public schema for legacy reasons
            "net/corda/db/schema/messagebus/db.changelog-master.xml" to DbMetadata(defaultSchemaName = "public"),
            "net/corda/db/schema/rbac/db.changelog-master.xml" to DbMetadata(),
            "net/corda/db/schema/crypto/db.changelog-master.xml" to DbMetadata()
        ).filterOnSchemasToGenerate().also { logger.info("Using the following schemas $it") }.forEach(::generateSql)
    }

    private fun Map<String, DbMetadata>.filterOnSchemasToGenerate() =
        if (schemasToGenerate.isEmpty()) this else this.filter { file ->
            schemasToGenerate.any { schemaName ->
                file.key.contains(schemaName)
            }
        }

    private fun generateSql(file: Map.Entry<String, DbMetadata>) {
        // Grabs dirname above db.changelog-master.xml to derive the package
        val test = "([a-zA-Z0-9]+)/db\\.changelog-master\\.xml".toRegex()
        // Make .sql output file
        val schemaDefinitionName = checkNotNull(test.find(file.key)).groupValues.last()
        val outputFileName = "${outputDir.removeSuffix("/")}/${schemaDefinitionName}.sql"
        val schemaName = file.value.defaultSchemaName ?: schemaDefinitionName

        // This is a workaround to make liquibase play nicely with the logger that's on the class loader
        val oldCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = DatabaseBootstrapAndUpgrade.classLoader

        writerFactory(outputFileName).use { outputFile ->
            writeSchemaToFile(schemaName, outputFile, file)
        }

        Thread.currentThread().contextClassLoader = oldCl
    }

    private fun writeSchemaToFile(
        schemaName: String,
        outputFile: FileWriter,
        file: Map.Entry<String, DbMetadata>
    ) {
        // A curious feature of the liquibase connection is that if you attempt to generate multiple sql files against
        // the same one, only the first one ends up generating offline sql when using offline mode. Note that multiple
        // connections don't overwrite any previous databasechangelog.csv, it is still appended to on each invocation.
        val (connection, database) = connectionAndDatabase()
        connection.use {
            if (ignoreSchemaSql == false) {
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

            liquibaseFactory(file.key, database).update(Contexts(), outputFile)
        }
    }

    private fun connectionAndDatabase() = if (jdbcUrl == null) {
        val database = PostgresDatabase()
        val connection = OfflineConnection("offline:postgresql", ClassLoaderResourceAccessor())
        database.connection = connection
        connection.attached(database)
        Pair(connection, database)
    } else {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        Pair(connection, database)
    }
}
