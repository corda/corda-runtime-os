package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.CordaWriteServiceImpl
import net.corda.libs.kafka.topic.utils.KafkaTopicUtils
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
    KafkaTopicUtils.createTopic(topicName, 1, 1, kafkaProps)

    val configuration = ConfigFactory.parseString(File(args[2]).readText())
    val configPlusTopic = configuration.withValue("topicName", ConfigValueFactory.fromAnyRef(topicName))

    val packageVersion = CordaConfigurationVersion("corda", 1, 0)
    val componentVersion = CordaConfigurationVersion("corda", 1, 0)
    val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)

    val configurationWriteService = CordaWriteServiceImpl()

    configurationWriteService.updateConfiguration(configurationKey, configPlusTopic)
}
