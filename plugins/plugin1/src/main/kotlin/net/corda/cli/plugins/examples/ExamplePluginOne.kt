package net.corda.cli.plugins.examples

import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.services.HttpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

// The PluginWrapper and Plugin are required for PF4J
class ExamplePluginOne(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExamplePluginOne::class.java)
    }

    // Supply plugin startup logic here
    override fun start() {
        logger.debug("ExamplePluginOne.start()")
    }

    // Supply plugin tear down here
    override fun stop() {
        logger.debug("ExamplePluginOne.stop()")
    }

    @Extension
    @CommandLine.Command(
        name = "plugin-one",
        description = ["Example Plugin one using function based subcommands, and services"]
    )
    class ExamplePluginOneEntry : CordaCliPlugin, HttpServiceUser {

        @CommandLine.Mixin
        override lateinit var service: HttpService

        @CommandLine.Command(name = "basic-example", description = ["A basic subcommand that doesnt use services."])
        fun exampleSubCommand() {
            println("Hello from plugin one!")
            println(System.getProperty("user.home"))
        }

        @CommandLine.Command(
            name = "service-example",
            description = ["A subcommand that uses a service supplied by the host."]
        )
        fun exampleServiceSubCommand() {
            service.get("json")
        }
    }
}
