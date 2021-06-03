package net.corda.tools.kafka

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import org.osgi.service.component.annotations.Reference
import java.io.File
import kotlin.system.exitProcess

@Reference(service = KafkaTopicAdmin::class)
private lateinit var topicAdmin: KafkaTopicAdmin
@Reference(service = KafkaConfigWrite::class)
private lateinit var configWriter: KafkaConfigWrite


fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Required command line arguments: kafkaProps topicTemplate typesafeconfig")
        exitProcess(1)
    }

    val topic = topicAdmin.createTopic(File(args[0]).readText(), File(args[1]).readText())
    configWriter.updateConfig(topic.getString("topicName"), File(args[2]).readText())
}
