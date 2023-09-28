package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.existingTopicNamesWithPrefix
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType
import com.fasterxml.jackson.module.kotlin.readValue
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CommandLine.Command(name = "connect", description = ["Connects to Kafka broker to create topics"])
class CreateConnect : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address"],
        required = true
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Path to Kafka configuration file"]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for Kafka operations to complete in seconds"]
    )
    var wait: Long = 60

    @CommandLine.Option(
        names = ["-f", "--file"],
        description = ["Relative path of the Kafka topic configuration file in YAML format"]
    )
    var configFilePath: String? = null

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val timeoutMillis = (wait * 1000).toInt()
        val kafkaProperties = getKafkaProperties()
        kafkaProperties[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = timeoutMillis
        kafkaProperties[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = timeoutMillis

        val client = Admin.create(kafkaProperties)
        val topicConfigs = getGeneratedTopicConfigs()

        try {
            val existingTopicNames = client.existingTopicNamesWithPrefix(create!!.topic!!.namePrefix, wait)
            val existingTopicsToUpdate = topicConfigs.topics.map { it.name }.filter { existingTopicNames.contains(it) }

            if (existingTopicsToUpdate.isNotEmpty()) {
                println("The following topics already exist and will not be included in the configuration update: " +
                        existingTopicsToUpdate.joinToString { it })
            }

            val topicConfigsToCreate = topicConfigs.topics.filterNot { existingTopicsToUpdate.contains(it.name) }
            if (topicConfigsToCreate.isNotEmpty()) {
                createTopicsWithRetry(client, topicConfigsToCreate)
            }

            // create all ACLs (if entries already exist, they are overwritten)
            client.createAcls(getAclBindings(topicConfigs.acls)).all().get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

        Thread.currentThread().contextClassLoader = contextCL
    }

    private fun  createTopicsWithRetry(client: Admin, topicConfigs: List<Create.PreviewTopicConfiguration>) {
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

    fun getAclBindings(acls: List<Create.PreviewTopicACL>): List<AclBinding> {
        return acls.flatMap { acl ->
            val pattern = ResourcePattern(ResourceType.TOPIC, acl.topic, PatternType.LITERAL)
            val aclEntries = acl.users.flatMap { user ->
                user.operations.map { operation ->
                    AccessControlEntry("User:${user.name}", "*", AclOperation.fromString(operation), AclPermissionType.ALLOW)
                }
            }
            aclEntries.map { AclBinding(pattern, it) }
        }
    }

    fun getTopics(topicConfigs: List<Create.PreviewTopicConfiguration>) =
        topicConfigs.associate { topicConfig: Create.PreviewTopicConfiguration ->
            topicConfig.name to NewTopic(topicConfig.name, create!!.partitionOverride, create!!.replicaOverride)
                .configs(topicConfig.config)
        }

    fun getGeneratedTopicConfigs(): Create.PreviewTopicConfigurations = if (configFilePath == null) {
        create!!.getTopicConfigsForPreview()
    } else {
        // Simply read the info from provided file
        create!!.mapper.readValue(Files.readString(File(configFilePath!!).toPath()))
    }

    fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        if (kafkaConfig != null) {
            kafkaProperties.load(FileInputStream(kafkaConfig!!))
        }
        kafkaProperties[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServer
        return kafkaProperties
    }
}
