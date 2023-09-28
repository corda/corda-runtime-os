package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.existingTopicNamesWithPrefix
import org.apache.kafka.common.config.ConfigResource
import picocli.CommandLine
import picocli.CommandLine.ParentCommand
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


@CommandLine.Command(
    name = "connect",
    description = ["Connects to Kafka broker to delete topics"],
    mixinStandardHelpOptions = true
)
class DeleteConnect : Runnable {

    @ParentCommand
    var delete: Delete? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for deletion to complete in seconds"]
    )
    var wait: Long = 60

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val timeoutMillis = (wait * 1000).toInt()
        val kafkaProperties = delete!!.topic!!.getKafkaProperties()
        kafkaProperties[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = timeoutMillis
        kafkaProperties[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = timeoutMillis

        val client = Admin.create(kafkaProperties)

        try {
            val topicNames = client.existingTopicNamesWithPrefix(delete!!.topic!!.namePrefix, wait)

            if (topicNames.isEmpty()) {
                println("No matching topics found")
            } else {
                println("Deleting topics: ${topicNames.joinToString()}")
                val configOp = listOf(AlterConfigOp(ConfigEntry("retention.ms", "1"), AlterConfigOp.OpType.SET))
                val alterConfigs = topicNames.associate { ConfigResource(ConfigResource.Type.TOPIC, it) to configOp }
                client.incrementalAlterConfigs(alterConfigs).all().get(wait, TimeUnit.SECONDS)
                client.deleteTopics(topicNames).all().get(wait, TimeUnit.SECONDS)
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

        Thread.currentThread().contextClassLoader = contextCL
    }
}
