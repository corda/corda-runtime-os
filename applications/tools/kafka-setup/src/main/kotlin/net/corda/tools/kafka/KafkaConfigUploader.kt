package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.write.EphemeralConfigWriteService
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.Properties

@Suppress("SpreadOperator")
@Component(immediate = true)
class KafkaConfigUploader @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = EphemeralConfigWriteService::class)
    private var configWriter: EphemeralConfigWriteService,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting kafka setup tool...")

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

            kafkaConnectionProperties[KAFKA_BOOTSTRAP_SERVER] = getConfigValue(kafkaConnectionProperties, KAFKA_BOOTSTRAP_SERVER)

            val topicTemplate = parameters.topicTemplate
            if (topicTemplate != null) {
                logger.info("Creating topics")
                topicAdmin.createTopics(kafkaConnectionProperties, topicTemplate.readText())
                logger.info("Topics created")
                consoleLogger.info("Topic creation completed")
            }

            val configurationFile = parameters.configurationFile
            if (configurationFile != null) {
                logger.info("Writing config to topic")
                configWriter.updateConfig(
                    getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME),
                    getBootstrapConfig(kafkaConnectionProperties),
                    configurationFile.readText()
                )
                logger.info("Write complete")
                consoleLogger.info("Write of config to topic completed")
            }
            shutdownOSGiFramework()
        }
    }

    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.empty()
            .withValue(
                KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, KAFKA_BOOTSTRAP_SERVER))
            )
            .withValue(CONFIG_TOPIC_NAME, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME)))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX))))
    }

    private fun getConfigValue(kafkaConnectionProperties: Properties?, path: String): String {
        var configValue = System.getProperty(path)
        if (configValue == null && kafkaConnectionProperties != null) {
            configValue = kafkaConnectionProperties[path].toString()
        }

        if (configValue == null) {
            logger.error(
                "No $path property found! " +
                        "Pass property in via --kafka properties file or via -D$path"
            )
            shutdown()
        }
        return configValue
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down kafka setup tool")
        shutdownOSGiFramework()
        logger.info("Shutting down config uploader")
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["--kafka"], description = ["File containing Kafka connection properties" +
                " OR pass in -Dbootstrap.servers and -Dconfig.topic.name"]
    )
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    var topicTemplate: File? = null

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}