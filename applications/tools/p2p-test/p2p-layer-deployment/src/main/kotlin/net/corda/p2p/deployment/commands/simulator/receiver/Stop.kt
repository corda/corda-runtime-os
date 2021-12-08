package net.corda.p2p.deployment.commands.simulator.receiver

import net.corda.p2p.deployment.commands.simulator.StopSimulators
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "stop",
    showDefaultValues = true,
    description = ["Stop receiving messages"]
)
class Stop : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    override fun run() {
        println("Stopping DB-Sinks...")
        StopSimulators(namespaceName, "DB_SINK").run()
        println("Stopping receivers...")
        StopSimulators(namespaceName, "RECEIVER").run()
    }
}
