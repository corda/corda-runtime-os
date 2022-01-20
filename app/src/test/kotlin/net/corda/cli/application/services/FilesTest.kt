package net.corda.cli.application.services

import com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Paths


class FilesTest {

    @Test
    fun testCliHomeDirNoEnvVar() {

        withEnvironmentVariable("CORDA_CLI_HOME_DIR", "").execute {
            assertEquals(
                Paths.get(System.getProperty("user.home"), "/.corda/cli/").toFile().absolutePath,
                Files.cliHomeDir().absolutePath
            )
        }

    }

    @Test
    fun testCliHomeDirWithEnvVar() {

        withEnvironmentVariable("CORDA_CLI_HOME_DIR", "C:\\corda\\cli").execute {
            assertEquals(
                "C:\\corda\\cli",
                Files.cliHomeDir().absolutePath
            )
        }
    }

}