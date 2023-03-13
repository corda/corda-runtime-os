package net.corda.cli.plugins.topicconfig

import net.corda.cli.api.CordaCliPlugin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.FileInputStream
import java.util.Properties

class TopicPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        val classLoader: ClassLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("Topic plugin started.")
    }

    override fun stop() {
        logger.info("Topic plugin stopped.")
    }

    @CommandLine.Command(name = "topic", subcommands = [Create::class, Delete::class], description = ["Plugin for Kafka topic operations."])
    class Topic : CordaCliPlugin, ExtensionPoint {

        @CommandLine.Option(
            names = ["-n", "--name-prefix"],
            description = ["Name prefix for topics"]
        )
        var namePrefix: String = ""

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

        fun getKafkaProperties(): Properties {
            val kafkaProperties = Properties()
            if (kafkaConfig != null) {
                kafkaProperties.load(FileInputStream(kafkaConfig!!))
            }
            kafkaProperties[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServer
            return kafkaProperties
        }

        fun getKafkaTopicsCommand(): String {
            var command = "kafka-topics.sh --bootstrap-server $bootstrapServer"
            if (kafkaConfig != null) {
                command += " --command-config $kafkaConfig"
            }
            return command
        }

        fun getKafkaAclsCommand(): String {
            var command = "kafka-acls.sh --bootstrap-server $bootstrapServer"
            if (kafkaConfig != null) {
                command += " --command-config $kafkaConfig"
            }
            return command
        }

    }

}
