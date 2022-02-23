package net.corda.introspiciere.cli

import net.corda.introspiciere.http.IntrospiciereHttpClient
import picocli.CommandLine
import picocli.CommandLine.Option

/**
 * Create a Kafka topic.
 */
@CommandLine.Command(name = "create-topic")
class CreateTopicCommand : BaseCommand() {

    @Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @Option(names = ["--partitions"], description = ["Number of partitions when creating the topic."])
    private var partitions: Int? = null

    @Option(names = ["--replication-factor"], description = ["Replication factor of the topic."])
    private var replicationFactor: Short? = null

    @Option(names = ["-c", "--config"], description = ["Additional topic config. Format KEY=VALUE."])
    private var configArray: Array<String> = emptyArray()

    override fun run() {
        val config = configArray.map { it.split("=") }.associate { it[0] to it[1] }
        IntrospiciereHttpClient(endpoint).createTopic(topicName, partitions, replicationFactor, config)
        println("Topic $topicName created successfully")
    }
}