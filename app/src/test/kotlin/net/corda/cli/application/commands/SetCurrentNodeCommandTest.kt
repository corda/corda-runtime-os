package net.corda.cli.application.commands

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import net.corda.cli.application.App
import net.corda.cli.application.services.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.io.FileInputStream
import java.util.UUID


class SetCurrentNodeCommandTest {

    @Test
    fun setCurrentNodeUrlTest() {

        val yaml = Yaml()
        val testUrl = "www.${UUID.randomUUID()}.com"
        var data: MutableMap<String, Any> = yaml.load(FileInputStream(Files.profile))

        //Ensure the url is going to change
        assertNotEquals(testUrl, data["url"])

        val outText = tapSystemOutNormalized {
            CommandLine(
                App()
            ).execute("set-node", "-t=$testUrl")
        }

        assertEquals("Target URL updated.\n", outText)

        data = yaml.load(FileInputStream(Files.profile))
        assertEquals(testUrl, data["url"])
    }
}