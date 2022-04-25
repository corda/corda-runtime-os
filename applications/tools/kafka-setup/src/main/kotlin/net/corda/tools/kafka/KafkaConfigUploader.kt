package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.configuration.publish.ConfigPublishService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Boot.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
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
import java.util.*
import kotlin.random.Random

@Suppress("SpreadOperator")
@Component(immediate = true)
class KafkaConfigUploader @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = ConfigPublishService::class)
    private var configPublish: ConfigPublishService,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
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
                configPublish.updateConfig(
                    CONFIG_TOPIC,
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
        val allConfig = ConfigFactory.empty()
            .withValue(
                BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, KAFKA_BOOTSTRAP_SERVER))
            )
            .withValue(
                MessagingConfig.Bus.BUS_TYPE,
                ConfigValueFactory.fromAnyRef("KAFKA")
            )
            .withValue(
                INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(Random.nextInt())
            )
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX)))
        return SmartConfigFactory.create(allConfig).create(allConfig)
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
                " OR pass in -Dbootstrap.servers"]
    )
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    var topicTemplate: File? = null

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}