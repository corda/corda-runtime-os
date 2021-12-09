package net.corda.p2p.deployment.commands.simulator.receiver

import picocli.CommandLine

@CommandLine.Command(
    subcommands = [
        Start::class,
        Status::class,
        Stop::class,
    ],
    mixinStandardHelpOptions = true,
    name = "receiver",
    description = ["Manage the receivers"]
)
class Receiver
