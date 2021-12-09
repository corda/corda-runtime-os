package net.corda.p2p.deployment.commands.simulator.db

import net.corda.p2p.deployment.commands.RunJar
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "jdbc",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Show the JDB connection URL"]
)
class Jdbc : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
    )
    private var namespaceName = Db.defaultName

    override fun run() {
        val status = Db.getDbStatus(namespaceName)
        if (status == null) {
            println("Database is not running")
            return
        }
        RunJar.startTelepresence()
        println("Example of JDBC URL: jdbc:postgresql://db.$namespaceName/${status.username}?user=${status.username}&password=${status.password}")
    }
}
