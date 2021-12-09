package net.corda.cli.plugins.demoA

import org.apache.commons.lang3.StringUtils
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.services.HttpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExampleNodePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExampleNodePlugin::class.java)
    }

    // Supply plugin startup logic here
    override fun start() {
        logger.debug("ExampleNodePlugin.start()")
        logger.debug(StringUtils.upperCase("ExampleNodePlugin"))
    }

    // Supply plugin tear down here
    override fun stop() {
        logger.debug("ExampleNodePlugin.stop()")
    }

    @Extension
    @CommandLine.Command(name = "node")
    class WelcomeCordaCliPlugin : CordaCliPlugin, HttpServiceUser {
        override lateinit var service: HttpService

        override val version: String
            get() = "0.0.1"
        override val pluginId: String
            get() = "ExampleNodePlugin"

        override fun setHttpService(httpService: HttpService) {
            this.service = httpService
        }

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
