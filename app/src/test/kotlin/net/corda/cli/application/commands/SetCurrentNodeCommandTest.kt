package net.corda.cli.application.commands

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable
import net.corda.cli.application.App
import net.corda.cli.application.services.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.io.FileInputStream
import java.io.FileWriter
import java.util.*
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
class SetCurrentNodeCommandTest {

    @Test
    fun setCurrentNodeUrlTest() {

        val randomPrefix = UUID.randomUUID()

        withEnvironmentVariable(
            "CORDA_CLI_HOME_DIR",
            "build/test-data/.cli-host/${randomPrefix}/"
        ).execute {
            Files.cliHomeDir().mkdirs()

            val testUrl = "www.${UUID.randomUUID()}.com"
            var data: MutableMap<String, Any> = mutableMapOf(Pair("url", "emptyUrl"))
            val yaml = Yaml()

            yaml.dump(data, FileWriter(Files.profile))

            CommandLine(
                App()
            ).execute("set-node", "-t=$testUrl")

            data = yaml.load(FileInputStream(Files.profile))
            assertEquals(testUrl, data["url"])
        }
    }
}