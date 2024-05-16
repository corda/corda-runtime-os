package net.corda.cli.commands.preinstall

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import picocli.CommandLine

class CheckPostgresTest {
    @Test
    fun testPostgresBootstrapEnabled() {
        val path = "./src/test/resources/PostgresTestBootstrapEnabled.yaml"
        val postgres = CheckPostgres()
        val ret = CommandLine(postgres).execute(path)
        assertEquals(1, ret)
    }
}
