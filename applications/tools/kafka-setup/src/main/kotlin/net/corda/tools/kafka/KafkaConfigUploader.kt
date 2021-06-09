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
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.system.exitProcess

@Component(immediate = true)
class KafkaConfigUploader @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = KafkaConfigWrite::class)
private var configWriter: KafkaConfigWrite
) : Application {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(KafkaConfigWrite::class.java)
    }

    override fun startup(args: Array<String>) {
        if (args.size != 3) {
            logger.error("Required command line arguments: kafkaProps topicTemplate typesafeConfig")
            exitProcess(1)
        }

        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(args[0]))

        val topic = topicAdmin.createTopic(kafkaConnectionProperties, File(args[1]).readText())
        configWriter.updateConfig(topic.getString("topicName"), File(args[2]).readText())
        shutdownOSGiFramework()
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