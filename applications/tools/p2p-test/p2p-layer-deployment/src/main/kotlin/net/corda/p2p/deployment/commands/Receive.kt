package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command

@Command(
    name = "receive",
    showDefaultValues = true,
    description = ["Start receiving messages"]
)
class Receive : RunSimulator() {
    override val parameters = mapOf("parallelClients" to 1, "simulatorMode" to "RECEIVER")
    override val filePrefix = "receive"
}
