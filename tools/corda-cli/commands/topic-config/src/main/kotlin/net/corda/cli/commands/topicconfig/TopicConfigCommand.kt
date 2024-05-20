package net.corda.cli.commands.topicconfig

import org.apache.kafka.clients.admin.AdminClientConfig
import picocli.CommandLine
import java.io.FileInputStream
import java.util.Properties

@CommandLine.Command(
    name = "topic",
    subcommands = [Create::class],
    description = ["Command for Kafka topic operations."],
    mixinStandardHelpOptions = true,
)
class TopicConfigCommand {

    companion object {
        val classLoader: ClassLoader = this::class.java.classLoader
    }

    @CommandLine.Option(
        names = ["-n", "--name-prefix"],
        description = ["Name prefix for topics"]
    )
    var namePrefix: String = ""

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address"],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Path to Kafka configuration file"]
    )
    var kafkaConfig: String? = null

    fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        if (kafkaConfig != null) {
            kafkaProperties.load(FileInputStream(kafkaConfig!!))
        }
        kafkaProperties[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServer
        return kafkaProperties
    }
}
