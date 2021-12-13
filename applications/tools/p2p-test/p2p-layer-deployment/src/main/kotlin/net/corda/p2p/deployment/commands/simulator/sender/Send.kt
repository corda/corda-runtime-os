package net.corda.p2p.deployment.commands.simulator.sender

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "send",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Send one off batch of messages"]
)
class Send : InvokeSender() {
    override val oneOff = true
    @Option(
        names = ["-t", "--total-number-of-messages"],
        description = ["Total number of messages"]
    )
    override var totalNumberOfMessages = 50L
}
