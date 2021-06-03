package net.corda.components.examples.bootstrap.topics

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.CordaWriteServiceFactory
import net.corda.libs.kafka.topic.utils.factory.TopicUtilsFactory
import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.StringReader
import java.util.*
import kotlin.system.exitProcess

@Component
class BootstrapTopics @Activate constructor(
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory,
    @Reference(service = CordaWriteServiceFactory::class)
    private val cordaWriteServiceFactory: CordaWriteServiceFactory
) : Application {


    override fun startup(args: Array<String>) {
        if (args.size != 3) {
            println("Required command line arguments: kafkaServerProperty topicName typesafeconfig")
            exitProcess(1)
        }

        val kafkaProps = Properties()
        kafkaProps.load(StringReader(args[0]))

        val topicName = args[1]
        val topicUtils = topicUtilsFactory.createTopicUtils(kafkaProps)
        topicUtils.createTopic(topicName, 1, 1)
        val configuration = ConfigFactory.load(args[2])
        val packageVersion = CordaConfigurationVersion("corda", 1, 0)
        val componentVersion = CordaConfigurationVersion("corda", 1, 0)
        val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)
        val configurationWriteService = cordaWriteServiceFactory.createWriteService(topicName)

        configurationWriteService.updateConfiguration(configurationKey, configuration)
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}