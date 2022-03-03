package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.stdout
import net.corda.introspiciere.cli.writeText
import picocli.CommandLine
import picocli.CommandLine.Option

/**
 * Create a Kafka topic.
 */
@CommandLine.Command(name = "create")
class CreateTopicCommand : BaseTopicCommand() {

    companion object {
        const val successMessage = "Topic %s created successfully"
    }

    @Option(names = ["--partitions"], description = ["Number of partitions when creating the topic."])
    private var partitions: Int? = null

    @Option(names = ["--replication-factor"], description = ["Replication factor of the topic."])
    private var replicationFactor: Short? = null

    @Option(names = ["-c", "--config"], description = ["Additional topic config. Format KEY=VALUE."])
    private var configArray: Array<String> = emptyArray()

    override fun run() {
        val config = configArray.map { it.split("=") }.associate { it[0] to it[1] }
        httpClient.createTopic(topicName, partitions, replicationFactor, config)
        successMessage.format(topicName).let(stdout::writeText)
    }
}
