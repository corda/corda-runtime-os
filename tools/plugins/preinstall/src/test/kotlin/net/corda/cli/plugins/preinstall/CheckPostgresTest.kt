package net.corda.cli.plugins.preinstall

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine

class CheckPostgresTest {

    @Test
    fun testPostgresBootstrapDisabled() {
        val path = "./src/test/resources/PostgresTestBootstrapDisabled.yaml"
        val postgres = CheckPostgres()
        CommandLine(postgres).execute(path)

        println(postgres.report)
        assertTrue(postgres.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))
        assertTrue(postgres.report.toString().contains("Get config DB PostgreSQL credentials for worker crypto config DB: FAILED"))
        assertTrue(postgres.report.toString()
            .contains("Get config DB PostgreSQL credentials for worker crypto state type keyRotation DB: FAILED"))
    }

    @Test
    fun testPostgresBootstrapEnabled() {
        val path = "./src/test/resources/PostgresTestBootstrapEnabled.yaml"
        val postgres = CheckPostgres()
        CommandLine(postgres).execute(path)

        assertTrue(postgres.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))
        assertTrue(postgres.report.toString().contains("Get bootstrap PostgreSQL credentials for database default: FAILED"))
        assertTrue(postgres.report.toString().contains("Get bootstrap PostgreSQL credentials for database state-manager: FAILED"))
    }

}