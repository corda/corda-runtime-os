package net.corda.tools.kafka

import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import org.osgi.service.component.annotations.Reference
import java.io.File
import kotlin.system.exitProcess

@Reference(service = KafkaTopicAdmin::class)
private lateinit var topicAdmin: KafkaTopicAdmin

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Required command line arguments: kafkaProps topicTemplate typesafeconfig")
        exitProcess(1)
    }

    topicAdmin.createTopic(File(args[0]).readText(), File(args[1]).readText())

//    val configuration = ConfigFactory.parseString(File(args[2]).readText())
//    val packageVersion = CordaConfigurationVersion("corda", 1, 0)
//    val componentVersion = CordaConfigurationVersion("corda", 1, 0)
//    val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)
//    //this need to go once bootstrapper is in place
//
//    val configurationWriteService =
//        CordaWriteServiceFactoryImpl(CordaKafkaPublisherFactory(AvroSchemaRegistryImpl())).createWriteService(topicName)
//
//    configurationWriteService.updateConfiguration(configurationKey, configuration)
}
