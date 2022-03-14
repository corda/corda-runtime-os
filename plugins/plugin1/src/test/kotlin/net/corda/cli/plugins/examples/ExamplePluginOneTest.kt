package net.corda.cli.plugins.examples

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import net.corda.cli.api.services.HttpService
import net.corda.cli.plugins.examples.mocks.MockHttpService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import picocli.CommandLine


class ExamplePluginOneTest {

    @Test
    fun testNoOptionCommand() {

        val app = ExamplePluginOne.ExamplePluginOneEntry()
        app.service = MockHttpService()

        val outText = tapSystemErrNormalized {
            CommandLine(
                app
            ).execute("")
        }

        assertTrue(outText.contains("Missing required options: '--user=<username>', '--password=<password>'"))
    }

    @Test
    fun testBasicExampleCommand() {

        val app = ExamplePluginOne.ExamplePluginOneEntry()
        app.service = MockHttpService()

        val outText = tapSystemOutNormalized {
            CommandLine(
                app
            ).execute("--user=test", "--password=test", "basic-example")
        }

        assertTrue(outText.contains("Hello from plugin one!"))
        assertTrue(outText.contains(System.getProperty("user.home")))
    }

    @Test
    fun testExampleServiceCommand() {

        val app = ExamplePluginOne.ExamplePluginOneEntry()
        app.service = MockHttpService()
        val url = "https://test.r3.com"
        val endpoint = "json"

        val outText = tapSystemOutNormalized {
            CommandLine(
                app
            ).execute("--user=test", "--password=test", "-t=$url", "service-example")
        }

        assertTrue(outText.contains("$url/$endpoint"))
    }
}