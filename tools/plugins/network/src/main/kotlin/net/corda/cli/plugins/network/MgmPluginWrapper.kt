package net.corda.cli.plugins.network

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.network.NetworkPluginWrapper.NetworkPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class MgmPluginWrapper : Plugin() {

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
            NetworkPlugin::class,
        ],
        description = ["Plugin for membership operations."]
    )
    class MgmPlugin : CordaCliPlugin
}
