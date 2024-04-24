package net.corda.sdk.bootstrap.dbconfig

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.OfflineConnection
import liquibase.database.core.PostgresDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.db.admin.LiquibaseSchemaUpdater
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DbSchemaGenerator(
    private val config: SpecConfig = SpecConfig(),
    private val liquibaseSchemaUpdater: LiquibaseSchemaUpdater = LiquibaseSchemaUpdaterImpl()
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)

        const val DEFAULT_CHANGELOG_PATH = "./databasechangelog.csv"

        // These should match the schema sub directory from the LIQUIBASEFILES list
        const val SCHEMA_OPTIONS = "config, messagebus, rbac, crypto, statemanager"

        // messagebus deliberately excluded as it's not used in Corda Clusters
        val DEFAULT_SCHEMA_OPTIONS = listOf("config", "rbac", "crypto")

        private val LIQUIBASEFILES = listOf(
            "net/corda/db/schema/config/db.changelog-master.xml",
            "net/corda/db/schema/messagebus/db.changelog-master.xml",
            "net/corda/db/schema/rbac/db.changelog-master.xml",
            "net/corda/db/schema/crypto/db.changelog-master.xml",
            "net/corda/db/schema/statemanager/db.changelog-master.xml"
        )
    }

    data class SpecConfig(
        val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
        val liquibaseFactory: (String, Database) -> Liquibase = { file: String, database: Database ->
            Liquibase(file, ClassLoaderResourceAccessor(), database)
        },
        val deleteFile: (Path) -> Unit = { path -> Files.deleteIfExists(path) },
        val jdbcConnectionFactory: (String?, String?, String?) -> Connection = { jdbcUrl, user, password ->
            DriverManager.getConnection(jdbcUrl, user, password)
        },
        val jdbcDatabaseFactory: (Connection) -> Database = { connection ->
            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
    )
    var clearChangeLog: Boolean = false
    var databaseChangeLogFile = Path.of(DEFAULT_CHANGELOG_PATH)
    var jdbcUrl: String? = null
    var user: String? = null
    var password: String? = null

    // This is a workaround to make liquibase play nicely with the logger that's on the class loader
    var classLoaderWorkaround: ClassLoader = this::class.java.classLoader

    fun generateSqlFilesForSchemas(
        schemasToGenerate: List<String> = DEFAULT_SCHEMA_OPTIONS,
        generateSchemaSql: List<String>? = null,
        outputDir: String = "."
    ) {
        if (clearChangeLog) {
            config.deleteFile(databaseChangeLogFile)
        }

        LIQUIBASEFILES.filter {
                file ->
            schemasToGenerate.any { schemaName ->
                file.contains(schemaName)
            }
        }.also { logger.info("Using the following schemas $it") }
            .forEach { generateSql(it, generateSchemaSql, outputDir) }
    }

    private fun generateSql(filename: String, generateSchemaSql: List<String>?, outputDir: String) {
        val schemaNameByType = processSchemaNameByTypeMap(generateSchemaSql)

        // Grabs dirname above db.changelog-master.xml to derive the package
        val test = "([a-zA-Z0-9]+)/db\\.changelog-master\\.xml".toRegex()
        // Make .sql output file
        val schemaType = checkNotNull(test.find(filename)).groupValues.last()
        val outputFileName = "${outputDir.removeSuffix("/")}/$schemaType.sql"

        val oldCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoaderWorkaround

        config.writerFactory(outputFileName).use { outputFile ->
            writeSchemaToFile(
                checkNotNull(schemaNameByType[schemaType]) { "Cannot find schema name from schema type derived from path" },
                outputFile,
                filename,
                !generateSchemaSql.isNullOrEmpty()
            )
        }
        Thread.currentThread().contextClassLoader = oldCl
    }

    private fun processSchemaNameByTypeMap(generateSchemaSql: List<String>?) =
        // Create the initial map out of our SCHEMA_OPTIONS, where each name is the same as the type
        SCHEMA_OPTIONS.split(",").map { it.trim() }.associateWith { it }.toMutableMap().apply {
            generateSchemaSql?.let { schemaNameOverrides ->
                // Allow empty list items but filter them, this allows the forcing of all defaults at the command line
                // by specifying "" as the parameter value
                schemaNameOverrides.filterNot { it.isEmpty() }.associate { schemaNameOption ->
                    // Otherwise split by : to override default schema names
                    schemaNameOption.split(":").let { splitOption ->
                        Pair(splitOption[0], splitOption[1])
                    }
                }.forEach { (schemaType, schemaName) ->
                    // For every override at the command line, put into our return map to replace the default
                    put(schemaType, schemaName)
                }
            }
        }.toMap()

    private fun writeSchemaToFile(
        schemaName: String,
        outputFile: FileWriter,
        filename: String,
        generateSchemaSql: Boolean
    ) {
        // A curious feature of the liquibase connection is that if you attempt to generate multiple sql files against
        // the same one, only the first one ends up generating offline sql when using offline mode. Note that multiple
        // connections don't overwrite any previous databasechangelog.csv, it is still appended to on each invocation.
        val (connection, database) = connectionAndDatabase()
        connection.use {
            if (generateSchemaSql) {
                // Our Liquibase files contain no schema information deliberately. Each db.changelog-master.xml
                // represents an isolated data set which could be put into its own database and therefore be separately
                // permissioned. If requested this tool will:
                // 1) Specify a schema for the current file in order that the tables get created under that schema
                // 2) Ensure liquibase uses that schema for any tracking tables
                // 3) Adds SQL to the output file to create the schema if it doesn't exist

                database.defaultSchemaName = schemaName // our tables
                database.liquibaseSchemaName = schemaName // liquibase tracking tables
                outputFile.write(System.lineSeparator())
                outputFile.write("CREATE SCHEMA IF NOT EXISTS $schemaName;")
                outputFile.write(System.lineSeparator())
                outputFile.write(System.lineSeparator())
            }

            val lb = config.liquibaseFactory(filename, database)
            liquibaseSchemaUpdater.update(lb, outputFile)
        }
    }

    private fun connectionAndDatabase() = if (jdbcUrl == null) {
        val database = PostgresDatabase()
        val connection =
            OfflineConnection("offline:postgresql?changeLogFile=$databaseChangeLogFile", ClassLoaderResourceAccessor())
        database.connection = connection
        connection.attached(database)
        Pair(connection, database)
    } else {
        val connection = config.jdbcConnectionFactory(jdbcUrl, user, password)
        val database = config.jdbcDatabaseFactory(connection)
        Pair(connection, database)
    }
}
