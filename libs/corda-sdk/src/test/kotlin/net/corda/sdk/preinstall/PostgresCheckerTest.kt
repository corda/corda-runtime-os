package net.corda.sdk.preinstall

import net.corda.sdk.preinstall.checker.PostgresChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostgresCheckerTest {

    @Test
    fun testPostgresBootstrapDisabled() {
        val path = "./src/test/resources/preinstall/PostgresTestBootstrapDisabled.yaml"
        val postgresChecker = PostgresChecker(path)
        val ret = postgresChecker.check()

        assertEquals(1, ret)
        assertTrue(postgresChecker.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))
        assertTrue(postgresChecker.report.toString().contains("Get config DB PostgreSQL credentials for worker crypto config DB: FAILED"))
        assertTrue(
            postgresChecker.report.toString()
                .contains("Get config DB PostgreSQL credentials for worker crypto state type keyRotation DB: FAILED")
        )
    }

    @Test
    fun testPostgresBootstrapEnabled() {
        val path = "./src/test/resources/preinstall/PostgresTestBootstrapEnabled.yaml"
        val postgresChecker = PostgresChecker(path)
        val ret = postgresChecker.check()

        assertEquals(1, ret)
        assertTrue(postgresChecker.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))
        assertTrue(postgresChecker.report.toString().contains("Get bootstrap PostgreSQL credentials for database default: FAILED"))
        assertTrue(postgresChecker.report.toString().contains("Get bootstrap PostgreSQL credentials for database state-manager: FAILED"))
    }
}
