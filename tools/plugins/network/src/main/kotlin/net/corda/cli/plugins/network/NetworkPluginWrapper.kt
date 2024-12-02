package net.corda.cli.plugins.network

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Suppress("unused")
class NetworkPluginWrapper {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(NetworkPlugin::class.java)
    }

    @CommandLine.Command(
        name = "network",
        subcommands = [
            GenerateGroupPolicy::class,
            Dynamic::class,
            GetRegistrations::class,
            Lookup::class,
            Operate::class,
            UpgradeCpi::class,
        ],
        hidden = true,
        mixinStandardHelpOptions = true,
        description = ["Plugin for interacting with a network."],
    )
    class NetworkPlugin

    @CommandLine.Command(
        name = "mgm",
        subcommands = [
            GenerateGroupPolicy::class,
        ],
        mixinStandardHelpOptions = true,
        description = ["Plugin for membership operations."],
    )
    class MgmPlugin
}
