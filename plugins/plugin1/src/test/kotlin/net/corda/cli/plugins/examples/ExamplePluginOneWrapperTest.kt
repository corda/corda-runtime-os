package net.corda.cli.plugins.examples

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import net.corda.cli.api.services.HttpService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import picocli.CommandLine


class ExamplePluginOneWrapperTest {

    // Unable to mock because of the picocli Options
    class MockHttpService : HttpService {

        @CommandLine.Option(names = ["-u", "--user"], description = ["User name"], required = true)
        override var username: String? = null

        @CommandLine.Option(names = ["-p", "--password"], description = ["Password"], required = true)
        override var password: String? = null

        @CommandLine.Option(names = ["-n", "--node-url"], description = ["The Swagger Url of the target Node."])
        override var url: String? = null

        override fun get(endpoint: String) { println("$url/$endpoint") }

        override fun put(endpoint: String, jsonBody: String) {}

        override fun patch(endpoint: String, jsonBody: String) {}

        override fun post(endpoint: String, jsonBody: String) {}

        override fun delete(endpoint: String) {}
    }

    @Test
    fun testNoOptionCommand() {

        val app = ExamplePluginOneWrapper.ExamplePluginOne()
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

        val app = ExamplePluginOneWrapper.ExamplePluginOne()
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

        val app = ExamplePluginOneWrapper.ExamplePluginOne()
        app.service = MockHttpService()
        val url = "https://test.r3.com"
        val endpoint = "exampleEndpoint"

        val outText = tapSystemOutNormalized {
            CommandLine(
                app
            ).execute("--user=test", "--password=test", "-n=$url", "service-example")
        }

        assertTrue(outText.contains("$url/$endpoint"))
    }
}