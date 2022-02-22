package net.corda.introspiciere.cli

import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.http.CreateTopicReq
import picocli.CommandLine

/**
 * Create a Kafka topic.
 */
@CommandLine.Command(name = "create-topic")
class CreateTopicCommand : BaseCommand() {

    @CommandLine.Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @CommandLine.Option(names = ["--partitions"], description = ["Number of partitions when creating the topic."])
    private var partitions: Int = TopicDefinition.DEFAULT_PARTITIONS

    @CommandLine.Option(names = ["--replication-factor"], description = ["Replication factor of the topic."])
    private var replicationFactor: Short = TopicDefinition.DEFAULT_REPLICATION_FACTOR

    override fun run() {
        var topic = TopicDefinition(topicName, partitions, replicationFactor)
        CreateTopicReq(topic).request(endpoint)
        println("Topic $topicName created successfully")
    }
}