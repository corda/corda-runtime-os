package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.CordaWriteServiceFactoryImpl
import net.corda.libs.kafka.topic.utils.factory.KafkaTopicUtilsFactory
import net.corda.messaging.kafka.publisher.factory.CordaKafkaPublisherFactory
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import java.io.File
import java.io.StringReader
import java.util.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Required command line arguments: kafkaServerProperty topicName typesafeconfig")
        exitProcess(1)
    }

    val kafkaProps = Properties()
    kafkaProps.load(StringReader(args[0]))

    val topicName = args[1]
    val topicUtils = KafkaTopicUtilsFactory().createTopicUtils(kafkaProps)
    topicUtils.createTopic(topicName, 1, 1)

    val configuration = ConfigFactory.parseString(File(args[2]).readText())
    val packageVersion = CordaConfigurationVersion("corda", 1, 0)
    val componentVersion = CordaConfigurationVersion("corda", 1, 0)
    val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)
    //this need to go once bootstrapper is in place

    val configurationWriteService =
        CordaWriteServiceFactoryImpl(CordaKafkaPublisherFactory(AvroSchemaRegistryImpl())).createWriteService(topicName)

    configurationWriteService.updateConfiguration(configurationKey, configuration)
}
