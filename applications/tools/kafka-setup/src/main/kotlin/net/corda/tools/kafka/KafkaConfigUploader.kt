package net.corda.tools.kafka

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.*

@Suppress("SpreadOperator")
@Component(immediate = true)
class KafkaConfigUploader @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = KafkaConfigWrite::class)
    private var configWriter: KafkaConfigWrite,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        const val KAFKA_CONFIG_TOPIC_NAME = "kafka.config.topic.name"
        const val KAFKA_BOOTSTRAP_SERVER = "kafka.bootstrap.servers"
        const val BOOTSTRAP_SERVER = "bootstrap.servers"
    }

    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val kafkaConnectionProperties = Properties()
            val kafkaPropertiesFile = parameters.kafkaConnection
            if (kafkaPropertiesFile != null) {
                kafkaConnectionProperties.load(FileInputStream(kafkaPropertiesFile))
            }

            setBootstrapServersProperty(kafkaConnectionProperties)

            val topicTemplate = parameters.topicTemplate
            if (topicTemplate != null) {
                logger.info("Creating topics")
                topicAdmin.createTopics(kafkaConnectionProperties, topicTemplate.readText())
                logger.info("Topics created")
            }

            val configurationFile = parameters.configurationFile
            if (configurationFile != null) {
                logger.info("Writing config to topic")
                configWriter.updateConfig(
                    getConfigTopicName(kafkaConnectionProperties),
                    kafkaConnectionProperties,
                    configurationFile.readText()
                )
                logger.info("Write complete")
            }
            shutdownOSGiFramework()
        }
    }

    private fun getConfigTopicName(kafkaConnectionProperties: Properties): String {
        var configTopicName = System.getProperty(KAFKA_CONFIG_TOPIC_NAME)
        if (configTopicName == null) {
            val configTopicNameProperty = kafkaConnectionProperties[KAFKA_CONFIG_TOPIC_NAME]
            if (configTopicNameProperty == null) {
                logger.error(
                    "No config topic defined! " +
                            "Pass config topic name in via kafka.properties file or via -Dkafka.config.topic.name"
                )
                shutdownOSGiFramework()
            } else {
                configTopicName = configTopicNameProperty.toString()
            }
        }
        return configTopicName
    }

    private fun setBootstrapServersProperty(kafkaConnectionProperties : Properties) {
        val kafkaBootStrapServers = System.getProperty(KAFKA_BOOTSTRAP_SERVER)
        if (kafkaBootStrapServers != null) {
            kafkaConnectionProperties[BOOTSTRAP_SERVER] = kafkaBootStrapServers
        }

        if (kafkaConnectionProperties[BOOTSTRAP_SERVER] == null) {
            logger.error("No bootstrap.servers property found! " +
                    "Pass property in via kafka.properties file or via -Dkafka.bootstrap.servers")
            shutdownOSGiFramework()
        }
    }

    override fun shutdown() {
        shutdownOSGiFramework()
        logger.info("Shutting down config uploader")
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties" +
            " OR pass in -Dkafka.bootstrap.servers and -Dkafka.config.topic.name"])
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    var topicTemplate: File? = null

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}