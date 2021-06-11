package net.corda.tools.kafka.reader

import net.corda.comp.kafka.config.read.KafkaConfigRead
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
class KafkaConfigReader @Activate constructor(
    @Reference(service = KafkaConfigRead::class)
    private var configReader: KafkaConfigRead
) : Application {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(KafkaConfigReader::class.java)
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

            configReader.startSubscription(parameters.topicName, kafkaConnectionProperties)
            logger.info("-------Sleeping for 10 seconds-------")
            Thread.sleep(10000)
            logger.info("-------Waking up-------")
            val configs = configReader.getAllConfigurations()

            for(config in configs) {
                logger.info("-------List of available configurations-------")
                logger.info("${config.key} -> ${config.value}")
            }
            shutdownOSGiFramework()
        }
    }

    override fun shutdown() {
        logger.info("Shutting down config reader")
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(KafkaConfigReader::class.java).bundleContext
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

    @CommandLine.Option(names = ["--topic"], description = ["String topic name"])
    lateinit var topicName: String

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}