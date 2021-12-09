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
class ExamplePluginOneWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExamplePluginOneWrapper::class.java)
    }

    // Supply plugin startup logic here
    override fun start() {
        logger.debug("ExampleNodePlugin.start()")
    }

    // Supply plugin tear down here
    override fun stop() {
        logger.debug("ExampleNodePlugin.stop()")
    }

    @Extension
    @CommandLine.Command(name = "pluginOne", description = ["Example Plugin one using function based subcommands, and services"])
    class ExamplePluginOne : CordaCliPlugin, HttpServiceUser {
        override lateinit var service: HttpService

        @CommandLine.Command(name = "basicExample", description = ["A basic subcommand that doesnt use services."])
        fun exampleSubCommand() {
            println("Hello from plugin one!")
        }

        @CommandLine.Command(name = "serviceExample", description = ["A subcommand that uses a service supplied by the host."])
        fun exampleServiceSubCommand() {
            println(service.get())
        }
    }
}
