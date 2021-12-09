package net.corda.p2p.deployment.commands.simulator

import net.corda.p2p.deployment.commands.simulator.db.Db
import net.corda.p2p.deployment.commands.simulator.receiver.Receiver
import net.corda.p2p.deployment.commands.simulator.sender.Sender
import picocli.CommandLine.Command

@Command(
    subcommands = [
        Db::class,
        Receiver::class,
        Sender::class,
    ],
    header = ["Manage the simulator app"],
    name = "simulator",
)
class Simulator
