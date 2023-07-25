package net.corda.cli.plugins.preinstall

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckPostgresTest {

    @Test
    fun testPostgresFileParsing() {
        val path = "./src/test/resources/PostgresTest.yaml"
        val postgres = CheckPostgres()
        CommandLine(postgres).execute(path)

        assertTrue(postgres.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))
    }

}