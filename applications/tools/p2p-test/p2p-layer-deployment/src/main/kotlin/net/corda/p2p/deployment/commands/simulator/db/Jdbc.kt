package net.corda.p2p.deployment.commands.simulator.db

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "jdbc",
    showDefaultValues = true,
    description = ["Show the JDB connection URL"]
)
class Jdbc : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
    )
    private var namespaceName = Db.defaultName

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
        val status = Db.getDbStatus(namespaceName)
        if (status == null) {
            println("Database is not running")
            return
        }
        startTelepresence()
        println("Example of JDBC URL: jdbc:postgresql://db.$namespaceName/${status.username}?user=${status.username}&password=${status.password}")
    }
}
