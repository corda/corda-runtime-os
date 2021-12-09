package net.corda.p2p.deployment.commands.simulator.sender

import picocli.CommandLine.Command

@Command(
    subcommands = [
        Send::class,
        Start::class,
        Status::class,
        Stop::class,
    ],
    name = "sender",
    description = ["Manage the senders"]
)
class Sender
