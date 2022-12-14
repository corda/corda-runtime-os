package net.corda.cli.plugins.topicconfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter

@CommandLine.Command(name = "script", description = ["Generates a script for the creation of Kafka topics"])
class CreateScript(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) }
) : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    @CommandLine.Option(
        names = ["-f", "--file"],
        description = ["File to write deletion script to"]
    )
    var file: String? = null

    @CommandLine.Option(
        names = ["-c", "--concurrency"],
        description = ["Number of topics to create concurrently"]
    )
    var concurrency: Int = 6

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun createTopicScripts(
        topicName: String,
        partitions: Int,
        replicas: Short,
        config: Map<String, String>
    ): List<String> {
        @Suppress("MaxLineLength")
        return listOf("${create!!.topic!!.getKafkaTopicsCommand()} --partitions $partitions --replication-factor $replicas --create --if-not-exists --topic $topicName ${createConfigString(config)} &")
    }

    fun createConfigString(config: Map<String, String>): String {
        if (config.entries.isNotEmpty()) {
            val values = config.entries.map { configEntry ->
                "--config \"${configEntry.key}=${configEntry.value}\""
            }.joinToString(" ")
            return values
        } else {
            return ""
        }
    }

    fun createACLs(topic: String, consumers: List<String>, producers: List<String>): List<String> {
        val consumerACLs = create!!.getUsersForProcessors(consumers)
            .map { user ->
                listOf(
                    "${create!!.topic!!.getKafkaAclsCommand()} --add --allow-principal User:$user --operation read --topic $topic &",
                    "${create!!.topic!!.getKafkaAclsCommand()} --add --allow-principal User:$user --operation describe --topic $topic &"
                )
            }.flatten()
        val producerACLs = create!!.getUsersForProcessors(producers)
            .map { user ->
                listOf(
                    "${create!!.topic!!.getKafkaAclsCommand()} --add --allow-principal User:$user --operation write --topic $topic &",
                    "${create!!.topic!!.getKafkaAclsCommand()} --add --allow-principal User:$user --operation describe --topic $topic &"
                )
            }.flatten()

        return consumerACLs + producerACLs
    }

    override fun run() {
        val topicConfigs = create!!.getTopicConfigs()

        val topics = topicConfigs.flatMap { topicConfig: Create.TopicConfig ->
            val topicName = create!!.getTopicName(topicConfig)
            val topicScripts = createTopicScripts(topicName, create!!.partitionOverride, create!!.replicaOverride, topicConfig.config)
            val acls = createACLs(topicName, topicConfig.consumers, topicConfig.producers)
            topicScripts + acls
        }

        val batchedTopics = topics.flatMapIndexed{ index: Int, topic: String ->
            if (index % concurrency == 0 || index == topics.size-1) {
                listOf(topic, "wait")
            } else {
                listOf(topic)
            }
        }

        if (file != null) {
            logger.info("Writing to path $file")
            val writer = writerFactory(file!!)
            writer.write(batchedTopics.joinToString(System.lineSeparator()))
            writer.flush()
            writer.close()
        } else {
            println(batchedTopics.joinToString(System.lineSeparator()))
        }
    }
}
