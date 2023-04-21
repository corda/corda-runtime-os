package net.corda.cli.plugins.mgm

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class MgmPluginWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MgmPluginWrapper::class.java)
    }

    override fun start() {
        logger.debug("MGM plugin started.")
    }

    override fun stop() {
        logger.debug("MGM plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "mgm",
        subcommands = [
            GenerateGroupPolicy::class,
            OnBoard::class,
        ],
        description = ["Plugin for membership operations."]
    )
    class MgmPlugin : CordaCliPlugin
}
