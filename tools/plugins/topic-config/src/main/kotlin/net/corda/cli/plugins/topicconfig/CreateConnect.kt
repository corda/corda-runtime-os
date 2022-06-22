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
        description = ["Time to wait for Kafka operations to complete in seconds"]
    )
    var wait: Long = 30

    @CommandLine.Option(
        names = ["-d", "--delete"],
        description = ["Delete existing topics with prefix before creating new ones"]
    )
    var delete: Boolean = false

    override fun run() {
        val client = Admin.create(create!!.topic!!.getKafkaProperties())
        val topicConfigs = create!!.getTopicConfigs().map { it.copy(name = create!!.getTopicName(it)) }

        try {
            val existingTopicNames = client.listTopics().names().get(wait, TimeUnit.SECONDS)
                .filter { it.startsWith(create!!.topic!!.namePrefix) }

            val topicConfigsToProcess = if (delete) {
                println("Deleting existing topics: ${existingTopicNames.joinToString()}")
                client.deleteTopics(existingTopicNames).all().get(wait, TimeUnit.SECONDS)
                topicConfigs
            } else {
                val existingTopicsToIgnore = topicConfigs.map { it.name }.filter { existingTopicNames.contains(it) }
                if (existingTopicsToIgnore.isNotEmpty()) {
                    println("Ignoring existing topics: ${existingTopicsToIgnore.joinToString { it }}")
                }
                topicConfigs.filterNot { existingTopicsToIgnore.contains(it.name) }
            }

            if (topicConfigsToProcess.isNotEmpty()) {
                val topics = getTopics(topicConfigsToProcess)
                println("Creating topics: ${topics.joinToString { it.name() }}")
                client.createTopics(topics).all().get(wait, TimeUnit.SECONDS)
                client.createAcls(getAclBindings(topicConfigsToProcess)).all().get()
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    }

    fun getAclBindings(topicConfigs: List<Create.TopicConfig>) =
        topicConfigs.flatMap { topicConfig: Create.TopicConfig ->
            val pattern = ResourcePattern(ResourceType.TOPIC, topicConfig.name, PatternType.LITERAL)
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
            NewTopic(topicConfig.name, create!!.partitionOverride, create!!.replicaOverride)
                .configs(topicConfig.config)
        }

}
