package net.corda.cli.plugins.dbconfig

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.OfflineConnection
import liquibase.database.core.PostgresDatabase
import liquibase.resource.ClassLoaderResourceAccessor
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
        names = ["-c", "--clearChangeLog"],
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
        if (clearChangeLog == true) {
            deleteFile(databaseChangeLogFile)
        }

        val files = listOf(
            "net/corda/db/schema/config/db.changelog-master.xml",
            "net/corda/db/schema/messagebus/db.changelog-master.xml",
            "net/corda/db/schema/rbac/db.changelog-master.xml",
        )

        val filteredFiles = if (schemasToGenerate.isEmpty()) files else files.filter { file ->
            schemasToGenerate.any { schemaName ->
                file.contains(schemaName)
            }
        }

        logger.info("Using the following schemas $filteredFiles")

        filteredFiles.forEach { file ->
            // Grabs dirname above db.changelog-master.xml to derive the package
            val test = "([a-zA-Z0-9]+)/db\\.changelog-master\\.xml".toRegex()
            // Make .sql output file
            val outputFileName = "${outputDir.removeSuffix("/")}/${test.find((file))!!.groupValues.last()}.sql"
            val outputFile = writerFactory(outputFileName)
            val database = PostgresDatabase()
            val connection = OfflineConnection(
                "offline:postgresql",
                ClassLoaderResourceAccessor()
            )
            database.connection = connection
            connection.attached(database)
            liquibaseFactory(file, database)
                .update(
                    Contexts(),
                    outputFile
                )
            outputFile.flush()
            outputFile.close()
        }
    }
}
