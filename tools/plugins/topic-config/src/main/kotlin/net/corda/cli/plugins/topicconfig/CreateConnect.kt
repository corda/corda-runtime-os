package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType
import picocli.CommandLine
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CommandLine.Command(name = "connect", description = ["Connects to Kafka broker to create topics"])
class CreateConnect : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for Kafka operations to complete in seconds"]
    )
    var wait: Long = 60

    @CommandLine.Option(
        names = ["-d", "--delete"],
        description = ["Delete existing topics with prefix before creating new ones"]
    )
    var delete: Boolean = false

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val client = Admin.create(create!!.topic!!.getKafkaProperties())
        val topicConfigs = create!!.getTopicConfigs().map { it.copy(name = create!!.getTopicName(it)) }

        try {
            val existingTopicNames = client.existingTopicNamesWithPrefix(create!!.topic!!.namePrefix, wait)

            val topicConfigsToProcess = if (delete) {
                if (existingTopicNames.isNotEmpty()) {
                    println("Deleting existing topics: ${existingTopicNames.joinToString()}")
                    val configOp = listOf(AlterConfigOp(ConfigEntry("retention.ms", "1"), AlterConfigOp.OpType.SET))
                    val alterConfigs = existingTopicNames.associate { ConfigResource(ConfigResource.Type.TOPIC, it) to configOp }
                    client.incrementalAlterConfigs(alterConfigs).all().get(wait, TimeUnit.SECONDS)
                    client.deleteTopics(existingTopicNames).all().get(wait, TimeUnit.SECONDS)
                }
                topicConfigs
            } else {
                val existingTopicsToIgnore = topicConfigs.map { it.name }.filter { existingTopicNames.contains(it) }
                if (existingTopicsToIgnore.isNotEmpty()) {
                    println("Ignoring existing topics: ${existingTopicsToIgnore.joinToString { it }}")
                }
                topicConfigs.filterNot { existingTopicsToIgnore.contains(it.name) }
            }

            if (topicConfigsToProcess.isNotEmpty()) {
                createTopicsWithRetry(client, topicConfigsToProcess)
                client.createAcls(getAclBindings(topicConfigsToProcess)).all().get()
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

        Thread.currentThread().contextClassLoader = contextCL
    }

    private fun createTopicsWithRetry(client: Admin, topicConfigs: List<Create.TopicConfig>) {
        val topics = getTopics(topicConfigs).toMutableMap()
        println("Creating topics: ${topics.keys.joinToString { it }}")
        val end = LocalDateTime.now().plusSeconds(wait)
        while (true) {
            if (topics.isEmpty()) {
                break
            } else {
                if (LocalDateTime.now().isAfter(end)) {
                    throw TimeoutException("Timed out creating topics")
                }
                createTopics(client, topics)
                Thread.sleep(1000)
            }
        }
    }

    private fun createTopics(client: Admin, topics: MutableMap<String, NewTopic>) {
        val errors = mutableSetOf<ExecutionException>()
        client.createTopics(topics.values).values().forEach { (topic, future) ->
            try {
                future.get(wait, TimeUnit.SECONDS)
                println("Created topic $topic")
                topics.remove(topic)
            } catch (e: ExecutionException) {
                if (e.cause is TopicExistsException) {
                    println("Topic $topic exists - will try again")
                } else {
                    println("Failed to create topic $topic: ${e.message}")
                    errors.add(e)
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw errors.first()
        }
    }

    fun getAclBindings(topicConfigs: List<Create.TopicConfig>) =
        topicConfigs.flatMap { topicConfig: Create.TopicConfig ->
            val pattern = ResourcePattern(ResourceType.TOPIC, topicConfig.name, PatternType.LITERAL)
            val consumerEntries = create!!.getUsersForProcessors(topicConfig.consumers)
                .map { user ->
                    AccessControlEntry("User:$user", "*", AclOperation.READ, AclPermissionType.ALLOW)
                }
            val producerEntries = create!!.getUsersForProcessors(topicConfig.producers)
                .map { user ->
                    AccessControlEntry("User:$user", "*", AclOperation.WRITE, AclPermissionType.ALLOW)
                }
            (consumerEntries + producerEntries).map { AclBinding(pattern, it) }
        }

    fun getTopics(topicConfigs: List<Create.TopicConfig>) =
        topicConfigs.map { topicConfig: Create.TopicConfig ->
            topicConfig.name to NewTopic(topicConfig.name, create!!.partitionOverride, create!!.replicaOverride)
                .configs(topicConfig.config)
        }.toMap()

}
