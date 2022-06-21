package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType
import picocli.CommandLine
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@CommandLine.Command(name = "connect", description = ["Connects to Kafka broker to create topics"])
class CreateConnect : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for creation to complete in seconds"]
    )
    var wait: Long = 30

    override fun run() {
        val client = Admin.create(create!!.topic!!.getKafkaProperties())
        val topicConfigs = create!!.getTopicConfigs()

        try {
            val topics = getTopics(topicConfigs)
            println("Creating topics: ${topics.joinToString { it.name() }}")
            client.createTopics(topics).all().get(wait, TimeUnit.SECONDS)
            client.createAcls(getAclBindings(topicConfigs)).all().get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    }

    fun getAclBindings(topicConfigs: List<Create.TopicConfig>) =
        topicConfigs.flatMap { topicConfig: Create.TopicConfig ->
            val pattern = ResourcePattern(ResourceType.TOPIC, create!!.getTopicName(topicConfig), PatternType.LITERAL)
            val consumerEntries = topicConfig.consumers.map { consumer ->
                AccessControlEntry("User:$consumer", "*", AclOperation.READ, AclPermissionType.ALLOW)
            }
            val producerEntries = topicConfig.producers.map { producer ->
                AccessControlEntry("User:$producer", "*", AclOperation.WRITE, AclPermissionType.ALLOW)
            }
            (consumerEntries + producerEntries).map { AclBinding(pattern, it) }
        }

    fun getTopics(topicConfigs: List<Create.TopicConfig>) =
        topicConfigs.map { topicConfig: Create.TopicConfig ->
            NewTopic(create!!.getTopicName(topicConfig), create!!.partitionOverride, create!!.replicaOverride)
                .configs(topicConfig.config)
        }

}
