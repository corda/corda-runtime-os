package net.corda.cli.plugins.topicconfig

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.FileInputStream
import java.util.Properties

class VersionProvider : AbstractCordaCliVersionProvider()

class TopicPlugin : Plugin() {

    companion object {
        val classLoader: ClassLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("Topic plugin started.")
    }

    override fun stop() {
        logger.debug("Topic plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "topic",
        subcommands = [Create::class],
        description = ["Plugin for Kafka topic operations."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    class Topic : CordaCliPlugin {

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

}
