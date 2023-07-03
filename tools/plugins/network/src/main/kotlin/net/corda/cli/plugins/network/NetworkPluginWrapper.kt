package net.corda.cli.plugins.network
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Suppress("unused")
class NetworkPluginWrapper : Plugin() {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(NetworkPlugin::class.java)
    }

    override fun start() {
        logger.debug("Network plugin started.")
    }

    override fun stop() {
        logger.debug("Network plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "network",
        subcommands = [
            MemberList::class,
            GenerateGroupPolicy::class,
            OnBoard::class
        ],
        mixinStandardHelpOptions = true,
        description = ["Plugin for interacting with a network."]
    )
    class NetworkPlugin: CordaCliPlugin

    @Extension
    @CommandLine.Command(
        name = "mgm",
        subcommands = [
            GenerateGroupPolicy::class,
        ],
        mixinStandardHelpOptions = true,
        description = ["Plugin for membership operations."]
    )
    class MgmPlugin : CordaCliPlugin
}

