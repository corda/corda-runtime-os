package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "send",
    showDefaultValues = true,
    description = ["Start sender simulator"]
)
class Send : RunSimulator() {
    @Option(
        names = ["-p", "--peer"],
        description = ["The name of the peer namespace"]
    )
    private var peer = "two"

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
            "dbParams" to dbParams(peer),
            "loadGenerationParams" to loadGenerationParams,
        )
    }

    private val yamlReader = ObjectMapper(YAMLFactory()).reader()

    @Suppress("UNCHECKED_CAST")
    private val namespaces by lazy {
        val getAll = ProcessBuilder().command(
            "kubectl",
            "get",
            "namespace",
            "-o",
            "yaml"
        ).start()
        if (getAll.waitFor() != 0) {
            System.err.println(getAll.errorStream.reader().readText())
            throw DeploymentException("Could not get namespaces")
        }
        val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        items.map {
            it["metadata"] as Yaml
        }.filter {
            val annotations = it["annotations"] as? Yaml
            annotations?.get("type") == "p2p"
        }.associate {
            it["name"] as String to it["annotations"] as Yaml
        }
    }

    private val loadGenerationParams by lazy {
        val loadGenerationType = if (oneOff) {
            "ONE_OFF"
        } else {
            "CONTINUOUS"
        }
        mapOf(
            "peerX500Name" to namespaces[peer]?.get("x500-name"),
            "peerGroupId" to namespaces[peer]?.get("group-id"),
            "ourX500Name" to namespaces[namespaceName]?.get("x500-name"),
            "ourGroupId" to namespaces[namespaceName]?.get("group-id"),
            "loadGenerationType" to loadGenerationType,
            "batchSize" to batchSize,
            "interBatchDelay" to "${delay}ms",
            "messageSizeBytes" to messageSizeBytes,
            "totalNumberOfMessages" to totalNumberOfMessages,
        )
    }
}
