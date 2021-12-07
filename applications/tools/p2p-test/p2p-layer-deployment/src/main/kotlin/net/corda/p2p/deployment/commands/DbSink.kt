package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command

@Command(
    name = "db-sink",
    showDefaultValues = true,
    description = ["Start db sink simulator"]
)
class DbSink : RunSimulator() {
    override val parameters by lazy {
        mapOf(
            "parallelClients" to 1,
            "simulatorMode" to "DB_SINK",
            "dbParams" to dbParams(namespaceName)
        )
    }
}
