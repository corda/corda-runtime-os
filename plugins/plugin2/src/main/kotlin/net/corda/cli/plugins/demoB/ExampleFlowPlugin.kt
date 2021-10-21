package net.corda.cli.plugins.demoB

import org.apache.commons.lang3.StringUtils
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExampleFlowPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {
        logger.info("ExampleFlowPlugin.start()")
        logger.info(StringUtils.upperCase("ExampleFlowPlugin"))
    }

    override fun stop() {
        logger.info("ExampleFlowPlugin.stop()")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExampleFlowPlugin::class.java)
    }

    @Extension
    @CommandLine.Command(name="flow", subcommands = [StartFlowCommand::class, ListFlowsCommand::class])
    class WelcomeCordaCliCommand : CordaCliCommand {
        override val pluginID: String
            get() = "ExampleFlowPlugin"
    }
}

@CommandLine.Command(name = "start", description = ["Starts a flow on the connected node."])
class StartFlowCommand(): Runnable {
    override fun run() {
        println("Status: Connected")
    }
}

@CommandLine.Command(name = "listAvailable", description = ["Lists all flows available on the connected node."])
class ListFlowsCommand(): Runnable {
    override fun run() {
        println("flows: [")
        println("net.corda.flows.myFlow,")
        println("net.corda.flows.yourFlow,")
        println("net.corda.flows.ourFlow")
        println("]")
    }
}


