package net.corda.tools.kafka

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.File
import kotlin.system.exitProcess

@Component(immediate = true)
class KafkaConfigUploader @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = KafkaConfigWrite::class)
    private var configWriter: KafkaConfigWrite,
) //: Application
{

    fun startup(args: Array<String>) {
        if (args.size != 3) {
            println("Required command line arguments: kafkaProps topicTemplate typesafeConfig")
            exitProcess(1)
        }

        val topic = topicAdmin.createTopic(File(args[0]).readText(), File(args[1]).readText())
        configWriter.updateConfig(topic.getString("topicName"), File(args[2]).readText())
    }

    fun shutdown() {
        TODO("Not yet implemented")
    }
}