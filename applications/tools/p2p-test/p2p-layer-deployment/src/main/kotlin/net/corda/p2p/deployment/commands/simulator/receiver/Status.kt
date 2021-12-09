package net.corda.p2p.deployment.commands.simulator.receiver

import net.corda.p2p.deployment.commands.simulator.GetSimulatorStatus
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "status",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Show the receiving status"]
)
class Status : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    override fun run() {
        println("Running DB-Sinks:")
        GetSimulatorStatus(
            "DB_SINK",
            namespaceName
        )().forEach {
            println("\t${it.display(true)}")
        }

        println("Running Receivers:")
        GetSimulatorStatus(
            "RECEIVER",
            namespaceName
        )().forEach {
            println("\t${it.display(false)}")
        }
    }
}
