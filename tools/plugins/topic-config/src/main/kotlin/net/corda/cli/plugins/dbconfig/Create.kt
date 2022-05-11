package net.corda.cli.plugins.topicconfig

import net.corda.schema.Schemas
import net.corda.schema.TopicDef
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

@CommandLine.Command(name = "create", description = ["Generates schema scripts for kafka topics"])
class Create(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
) : Runnable {
    @CommandLine.Option(
        names = ["-r", "--replicas"],
        description = ["Override replica count globally"]
    )
    var replicaOverride: Int? = null
    @CommandLine.Option(
        names = ["-p", "--partitions"],
        description = ["Override partition count globally"]
    )
    var partitionOverride: Int? = null
    @CommandLine.Option(
        names = ["-f", "--prefix"],
        description = ["Set topic prefix for created topics"]
    )
    var topicPrefix: String? = null
    @CommandLine.Option(
        names = ["-a", "--address"],
        description = ["Bootstrap server address for topic create"],
        required = true
    )
    var bootstrapAddr: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputLocation: String? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun run() {
        val topicSchemas = Schemas::class.nestedClasses
            .flatMap { it.nestedClasses }
            .filter { it.primaryConstructor != null }
            .map { it.createInstance() }

        val topics = topicSchemas.flatMap {
            val topicDef = it as TopicDef
            // Add support for prefixed topic names
            val topicPrefix = topicPrefix ?: ""
            val topicName = if (topicPrefix.isEmpty()) topicDef.name else "$topicPrefix-${topicDef.name}"
            // Global partition count override
            val partitions = partitionOverride ?: topicDef.numPartitions
            // Global replica count override
            val replicas = replicaOverride ?: topicDef.replicationFactor
            val config = createConfigString(topicDef.config)
            return@flatMap listOf(
                "./kafka-topics.sh --bootstrap-server $bootstrapAddr --partitions $partitions --replication-factor $replicas --create --if-not-exists --topic $topicName $config &",
            )
        } + listOf(
            "wait",
            "echo -e 'Successfully created the following topics:'",
            "./kafka-topics.sh --bootstrap-server {{ include 'corda.kafkaBootstrapServers' . }} --list"
        )

        if (outputLocation != null){
            writerFactory(outputLocation!!).write(topics.joinToString(System.lineSeparator()))
        } else {
            println(topics.joinToString("\n"))
        }
    }

    private fun createConfigString(config: Map<String, String>): String {
        if (config.entries.isNotEmpty()) {
            val values = config.entries.map { configEntry ->
                return@map "\"--${configEntry.key}=${configEntry.value}\""
            }.joinToString(" ")
            return "--config $values"
        } else {
            return ""
        }
    }
}
