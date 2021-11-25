package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "send",
    description = ["Start sender simulator"]
)
class Send : RunSimulator() {
    override val filePrefix = "send"

    @Option(
        names = ["-p", "--peer"],
        description = ["The peer X500 name and group ID (<x500name>:<groupId>)"]
    )
    private var peer = "O=Alice,L=London,C=GB:group-1"

    @Option(
        names = ["-o", "--our"],
        description = ["Our peer X500 name and group ID (<x500name>:<groupId>)"]
    )
    private var our = "O=Bob,L=London,C=GB:group-1"

    @Option(
        names = ["-f", "--one-off"],
        description = ["One off generation type"]
    )
    private var oneOff = false

    @Option(
        names = ["-b", "--batch-size"],
        description = ["size of batch"]
    )
    private var batchSize = 10

    @Option(
        names = ["-d", "--delay"],
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

    override val parameters by lazy {
        mapOf(
            "parallelClients" to 1,
            "simulatorMode" to "SENDER",
            "dbParams" to dbParams,
            "loadGenerationParams" to loadGenerationParams,
        )
    }

    private val loadGenerationParams by lazy {
        val peerSplit = peer.split(":")
        if (peerSplit.size != 2) {
            throw RuntimeException("$peer should be in the format <x500name>:<groupId>")
        }
        val ourSplit = our.split(":")
        if (ourSplit.size != 2) {
            throw RuntimeException("$our should be in the format <x500name>:<groupId>")
        }
        val loadGenerationType = if (oneOff) {
            "ONE_OFF"
        } else {
            "CONTINUOUS"
        }
        mapOf(
            "peerX500Name" to peerSplit[0],
            "peerGroupId" to peerSplit[1],
            "ourX500Name" to ourSplit[0],
            "ourGroupId" to ourSplit[1],
            "loadGenerationType" to loadGenerationType,
            "batchSize" to batchSize,
            "interBatchDelay" to "${delay}ms",
            "messageSizeBytes" to messageSizeBytes,
            "totalNumberOfMessages" to totalNumberOfMessages,
        )
    }
}
