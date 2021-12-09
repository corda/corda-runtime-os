package net.corda.p2p.deployment.commands.simulator.sender

import net.corda.p2p.deployment.commands.simulator.GetSimulatorStatus
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "status",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Show the senders status"]
)
class Status : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    override fun run() {
        println("Running Senders:")
        GetSimulatorStatus(
            "SENDER",
            namespaceName
        )().forEach {
            println("\t${it.display(true)}")
        }
    }
}
