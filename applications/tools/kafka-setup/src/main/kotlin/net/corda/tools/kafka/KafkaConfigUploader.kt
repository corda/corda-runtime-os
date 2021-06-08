package net.corda.tools.kafka

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
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

        val topic = topicAdmin.createTopic(File(args[0]).readText(), File(args[1]).readText())
        configWriter.updateConfig(topic.getString("topicName"), File(args[2]).readText())
        shutdown()
    }

    override fun shutdown() {
        logger.info("Shutting down config uploader")
    }
}