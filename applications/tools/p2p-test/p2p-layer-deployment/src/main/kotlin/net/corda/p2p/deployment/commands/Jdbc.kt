package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "jdbc",
    description = ["Forward the database port"]
)
class Jdbc : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    private var namespaceName = "p2p-layer"

    private val port = PortDetector()

    override fun run() {
        val port = port.next()
        val commands = listOf(
            "kubectl",
            "port-forward",
            "-n",
            namespaceName,
            "service/db",
            "$port:psql"
        )
        println("Example of JDBC URL: jdbc:postgresql://localhost:$port/corda?user=corda&password=corda-p2p-masters")
        ProcessBuilder(commands)
            .inheritIO()
            .start()
            .waitFor()
    }
}
