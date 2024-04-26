package net.corda.cli.plugins.vnode.commands

import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import kotlin.io.path.writeLines
import kotlin.test.assertEquals

class PlatformMigrationSmokeTest {
    private companion object {
        // env variables are expected to be set by .ci/JenkinsfileCombinedWorkerPluginsSmokeTests
        private val jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/cordacluster"
        private val postgresUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: "postgres"
        private val postgresPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: "password"

        val validHoldingIds = listOf("30f232111e9a", "25ab40d125a6", "ebf080aaeb79")
    }

    private val holdingIdsFile = File.createTempFile("holdingIds", null).also {
        it.deleteOnExit()
        it.toPath().writeLines(validHoldingIds)
    }

    private val outputFile = File.createTempFile("vnodes", ".sql").also {
        it.deleteOnExit()
    }

    @Test
    fun `platform migration command works with short option names`() {
        val exitCode = CommandLine(PlatformMigration())
            .execute(
                "--jdbc-url=$jdbcUrl",
                "-u=$postgresUser",
                "-p=$postgresPassword",
                "-i=${holdingIdsFile.absolutePath}",
                "-o=${outputFile.absolutePath}"
            )
        assertEquals(0, exitCode)
        val updateDatabaseScriptLines = outputFile.readLines().filter {
            it.contains("-- Update Database Script")
        }
        assertEquals(3 * validHoldingIds.size, updateDatabaseScriptLines.size)
    }

    @Test
    fun `platform migration works with long option names`() {
        val exitCode = CommandLine(PlatformMigration())
            .execute(
                "--jdbc-url=$jdbcUrl",
                "--user=$postgresUser",
                "--password=$postgresPassword",
                "--input-filename=${holdingIdsFile.absolutePath}",
                "--output-filename=${outputFile.absolutePath}"
            )
        assertEquals(0, exitCode)
        val updateDatabaseScriptLines = outputFile.readLines().filter {
            it.contains("-- Update Database Script")
        }
        assertEquals(3 * validHoldingIds.size, updateDatabaseScriptLines.size)
    }

    @Test
    fun `use default filenames`() {
        File("./holdingIds").let {
            it.delete()
            it.createNewFile()
            it.deleteOnExit()
            it.toPath().writeLines(validHoldingIds)
        }
        val defaultOutputFile = File("./vnodes.sql").also {
            it.delete()
            it.deleteOnExit()
        }

        val exitCode = CommandLine(PlatformMigration())
            .execute(
                "--jdbc-url=$jdbcUrl",
                "--user=$postgresUser",
                "--password=$postgresPassword",
            )
        assertEquals(0, exitCode)
        val updateDatabaseScriptLines = defaultOutputFile.readLines().filter {
            it.contains("-- Update Database Script")
        }
        assertEquals(3 * validHoldingIds.size, updateDatabaseScriptLines.size)
    }
}