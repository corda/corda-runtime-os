package net.corda.p2p.deployment.commands.simulator.sender

import picocli.CommandLine.Command

@Command(
    name = "start",
    showDefaultValues = true,
    description = ["Send messages continuously"]
)
class Start : InvokeSender() {
    override val oneOff = false
}
