package net.corda.p2p.deployment.commands.simulator.receiver

import net.corda.p2p.deployment.commands.simulator.RunSimulator
import net.corda.p2p.deployment.commands.simulator.db.Db
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "start",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Start receiving messages"]
)
class Start : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    @Option(
        names = ["-d", "--db"],
        description = ["The name of the database to save messages to"],
    )
    private var dbName = Db.defaultName

    @Option(
        names = ["-r", "--receivers-count"],
        description = ["The number of receiver pods to start"],
    )
    private var receiversCount = 1

    @Option(
        names = ["-s", "--db-sink-count"],
        description = ["The number of db-sink pods to start"],
    )
    private var dbSinkCount = 1

    override fun run() {
        repeat(receiversCount) {
            RunSimulator(
                namespaceName,
                dbName,
                mapOf("parallelClients" to 1, "simulatorMode" to "RECEIVER"),
                false
            ).run()
        }
        repeat(dbSinkCount) {
            RunSimulator(
                namespaceName,
                dbName,
                mapOf("parallelClients" to 1, "simulatorMode" to "DB_SINK"),
                false
            ).run()
        }
    }
}
