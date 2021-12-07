package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "jdbc",
    showDefaultValues = true,
    description = ["Forward the database port"]
)
class Jdbc : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true,
    )
    private lateinit var namespaceName: String

    private fun startTelepresence() {
        ProcessBuilder()
            .command(
                "telepresence",
                "connect"
            )
            .inheritIO()
            .start()
            .waitFor()
    }

    override fun run() {
        startTelepresence()
        println("Example of JDBC URL: jdbc:postgresql://db.$namespaceName/corda?user=corda&password=corda-p2p-masters")
    }
}
