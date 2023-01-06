package net.corda.cli.plugins.dbconfig

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.OfflineConnection
import liquibase.database.core.PostgresDatabase
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.db.schema.DbSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.nio.file.Files.deleteIfExists
import java.nio.file.Path

@CommandLine.Command(name = "spec", description = ["Does database schema generation from liquibase"])
class Spec(
    private val databaseChangeLogFile: Path = Path.of("./databasechangelog.csv"),
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
    private val liquibaseFactory: (String, PostgresDatabase) -> Liquibase =
        { file: String, database: PostgresDatabase -> Liquibase(file, ClassLoaderResourceAccessor(), database) },
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
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputDir: String = "."

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun run() {
        val parentClassLoader = Database.classLoader
        if (clearChangeLog == true) {
            deleteFile(databaseChangeLogFile)
        }

        val files = mapOf(
            "net/corda/db/schema/config/db.changelog-master.xml" to DbMetadata(),
            "net/corda/db/schema/messagebus/db.changelog-master.xml" to DbMetadata(),
            "net/corda/db/schema/rbac/db.changelog-master.xml" to DbMetadata(),
            "net/corda/db/schema/crypto/db.changelog-master.xml" to DbMetadata(
                defaultSchemaName = DbSchema.CRYPTO
            )
        )

        val filteredFiles = if (schemasToGenerate.isEmpty()) files else files.filter { file ->
            schemasToGenerate.any { schemaName ->
                file.key.contains(schemaName)
            }
        }

        logger.info("Using the following schemas $filteredFiles")

        filteredFiles.forEach { file ->
            // Grabs dirname above db.changelog-master.xml to derive the package
            val test = "([a-zA-Z0-9]+)/db\\.changelog-master\\.xml".toRegex()
            // Make .sql output file
            val outputFileName = "${outputDir.removeSuffix("/")}/${test.find((file.key))!!.groupValues.last()}.sql"
            val outputFile = writerFactory(outputFileName)
            // This is a workaround to make liquibase play nicely with the logger that's on the class loader
            val oldCl = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = parentClassLoader
            val database = PostgresDatabase()
            if (!file.value.defaultSchemaName.isNullOrBlank()) {
                database.defaultSchemaName = file.value.defaultSchemaName
                outputFile.write(System.lineSeparator())
                outputFile.write("CREATE SCHEMA IF NOT EXISTS ${file.value.defaultSchemaName};")
                outputFile.write(System.lineSeparator())
                outputFile.write(System.lineSeparator())
            }
            val connection = OfflineConnection(
                "offline:postgresql",
                ClassLoaderResourceAccessor()
            )
            database.connection = connection
            connection.attached(database)
            liquibaseFactory(file.key, database)
                .update(
                    Contexts(),
                    outputFile
                )
            outputFile.flush()
            outputFile.close()
            Thread.currentThread().contextClassLoader = oldCl
        }
    }
}
