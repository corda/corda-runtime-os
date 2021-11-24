package net.corda.p2p.deployment

import picocli.CommandLine.Command

@Command(
    name = "db-sink",
    description = ["Start db sink simulator"]
)
class DbSink : RunSimulator() {
    override val parameters by lazy {
        mapOf("parallelClients" to 1, "simulatorMode" to "DB_SINK", "dbParams" to dbParams)
    }
    override val filePrefix = "db-sink"
}
