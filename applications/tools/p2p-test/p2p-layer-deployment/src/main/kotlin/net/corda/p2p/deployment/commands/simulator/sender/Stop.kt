package net.corda.p2p.deployment.commands.simulator.sender

import net.corda.p2p.deployment.commands.simulator.StopSimulators
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "stop",
    showDefaultValues = true,
    description = ["Stop sending messages"]
)
class Stop : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    override fun run() {
        println("Stopping senders...")
        StopSimulators(namespaceName, "SENDER").run()
    }
}
