package net.corda.p2p.deployment.commands.simulator.sender

import picocli.CommandLine.Command

@Command(
    name = "send",
    showDefaultValues = true,
    description = ["Send one off batch of messages"]
)
class Send : InvokeSender() {
    override val oneOff = true
}
