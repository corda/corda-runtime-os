package net.corda.p2p.deployment.commands.simulator.sender

import net.corda.p2p.deployment.commands.simulator.RunSimulator.Companion.namespaceAnnotation
import net.corda.p2p.deployment.commands.simulator.db.Db
import picocli.CommandLine.Option

abstract class InvokeSender : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace to send from"],
        required = true
    )
    private lateinit var namespaceName: String

    @Option(
        names = ["-d", "--db"],
        description = ["The name of the database to save messages to"],
    )
    private var dbName = Db.defaultName

    @Option(
        names = ["-p", "--peer"],
        description = ["The name of the peer namespace"],
        required = true
    )
    private lateinit var peer: String

    abstract val oneOff: Boolean

    @Option(
        names = ["-b", "--batch-size"],
        description = ["size of batch"]
    )
    private var batchSize = 10

    @Option(
        names = ["-y", "--delay"],
        description = ["delay in milliseconds"]
    )
    private var delay = 0L

    @Option(
        names = ["-s", "--message-size-bytes"],
        description = ["size message in bytes"]
    )
    private var messageSizeBytes = 10000L

    @Option(
        names = ["-t", "--total-number-of-messages"],
        description = ["Total number of messages (for one ofe case)"]
    )
    private var totalNumberOfMessages = 50L

    private val loadGenerationParams by lazy {
        val peerAnnotations = namespaceAnnotation(peer)
        val loadGenerationType = if (oneOff) {
            "ONE_OFF"
        } else {
            "CONTINUOUS"
        }
        mapOf(
            "peerX500Name" to peerAnnotations["x500-name"],
            "peerGroupId" to peerAnnotations["group-id"],
            "ourX500Name" to peerAnnotations["x500-name"],
            "ourGroupId" to peerAnnotations["group-id"],
            "loadGenerationType" to loadGenerationType,
            "batchSize" to batchSize,
            "interBatchDelay" to "${delay}ms",
            "messageSizeBytes" to messageSizeBytes,
            "totalNumberOfMessages" to totalNumberOfMessages,
        )
    }

    override fun run() {
        val parameters = mapOf(
            "parallelClients" to 1,
            "simulatorMode" to "SENDER",
            "loadGenerationParams" to loadGenerationParams,
        )
        net.corda.p2p.deployment.commands.simulator.RunSimulator(
            namespaceName,
            dbName,
            parameters,
            oneOff,
        ).run()
    }
}
