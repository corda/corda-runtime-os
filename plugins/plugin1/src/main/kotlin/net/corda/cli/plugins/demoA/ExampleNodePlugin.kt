package net.corda.cli.plugins.demoA

import org.apache.commons.lang3.StringUtils
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExampleNodePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {
        logger.info("ExampleNodePlugin.start()")
        logger.info(StringUtils.upperCase("ExampleNodePlugin"))
    }

    override fun stop() {
        logger.info("ExampleNodePlugin.stop()")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExampleNodePlugin::class.java)
    }

    @Extension
    @CommandLine.Command(name = "node", subcommands = [NodeStatusCommand::class, NodeAddressCommand::class])
    class WelcomeCordaCliCommand : CordaCliCommand {
        override val pluginID: String
            get() = "ExampleNodePlugin"
    }

}

@CommandLine.Command(name = "status", description = ["Prints the status of the connected node."])
class NodeStatusCommand(): Runnable {
    override fun run() {
        print("Status: Connected")
    }
}

@CommandLine.Command(name = "address", description = ["Prints the address of the connected node."])
class NodeAddressCommand(): Runnable {
    override fun run() {
        print("Addr: 1.1.1.1")
    }
}