package net.corda.tools.kafka

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private var configWriter: KafkaConfigWrite
) : Application {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
        const val configTopic = "topic.name"
    }

    override fun startup(args: Array<String>) {

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val kafkaConnectionProperties = Properties()
            kafkaConnectionProperties.load(FileInputStream(parameters.kafkaConnection))

            val topicTemplate = parameters.topicTemplate
            var configTopicName = kafkaConnectionProperties[configTopic].toString()
            if (topicTemplate != null) {
                logger.info("Creating topics")
                topicAdmin.createTopics(kafkaConnectionProperties, topicTemplate.readText())
            }

            val configurationFile = parameters.configurationFile
            if (configurationFile != null) {
                logger.info("Writing config to topic")
                configWriter.updateConfig(
                    configTopicName,
                    kafkaConnectionProperties,
                    configurationFile.readText()
                )
            }
            shutdownOSGiFramework()
        }
    }

    override fun shutdown() {
        logger.info("Shutting down config uploader")
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(KafkaConfigUploader::class.java).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    lateinit var kafkaConnection: File

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    var topicTemplate: File? = null

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}