package net.corda.cli.plugins.demoA

import org.apache.commons.lang3.StringUtils
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.api.services.HttpRpcService
import net.corda.cli.api.services.HttpType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExampleNodePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {
        logger.debug("ExampleNodePlugin.start()")
        logger.debug(StringUtils.upperCase("ExampleNodePlugin"))
    }

    override fun stop() {
        logger.debug("ExampleNodePlugin.stop()")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExampleNodePlugin::class.java)
    }

    @Extension
    @CommandLine.Command(name = "node", subcommands = [NodeStatusCommand::class, NodeAddressCommand::class, SendRequestCommand::class])
    class WelcomeCordaCliPlugin : CordaCliPlugin {
        override lateinit var service: HttpRpcService

        override val version: String
            get() = "0.0.1"
        override val pluginId: String
            get() = "ExampleNodePlugin"

        override fun setHttpService(httpRpcService: HttpRpcService) {
            this.service = httpRpcService
        }
    }

}

@CommandLine.Command(name = "status", description = ["Prints the status of the connected node."])
class NodeStatusCommand(): Runnable {
    override fun run() {
        println("Status: Connected")
    }
}

@CommandLine.Command(name = "address", description = ["Prints the address of the connected node."])
class NodeAddressCommand(): Runnable {
    override fun run() {
        println("Address: 1.1.1.1")
    }
}

@CommandLine.Command(name = "sendRequest", description = ["Sends Request to the connect node"])
class SendRequestCommand(): Runnable {

    override fun run() {
        val rpcService = HttpRpcService()

        println(rpcService.sendRequest(HttpType.GET, "{'id':'4587348907'}", "http://node1.com/rpc"))
    }
}