package net.corda.cli.commands.network

import picocli.CommandLine


@Suppress("unused")
class NetworkCommandWrapper {

    @CommandLine.Command(
        name = "network",
        subcommands = [
            GenerateGroupPolicy::class,
            Dynamic::class,
            GetRegistrations::class,
            Lookup::class,
            Operate::class,
        ],
        hidden = true,
        mixinStandardHelpOptions = true,
        description = ["Plugin for interacting with a network."],
    )
    class NetworkCommand

    @CommandLine.Command(
        name = "mgm",
        subcommands = [
            GenerateGroupPolicy::class,
        ],
        mixinStandardHelpOptions = true,
        description = ["Plugin for membership operations."],
    )
    class MgmCommand
}
