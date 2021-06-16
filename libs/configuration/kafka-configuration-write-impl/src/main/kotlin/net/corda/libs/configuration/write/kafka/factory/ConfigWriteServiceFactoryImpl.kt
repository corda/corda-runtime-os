package net.corda.libs.configuration.write.kafka.factory

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.factory.ConfigWriteServiceFactory
import net.corda.libs.configuration.write.kafka.ConfigWriteServiceImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*

/**
 * Kafka implementation for [ConfigWriteServiceFactory].
 * @property publisherFactory
 */
@Component(immediate = true, service = [ConfigWriteServiceFactory::class])
class ConfigWriteServiceFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigWriteServiceFactory {

    private val CONFIGURATION_WRITE_SERVICE = "CONFIGURATION_WRITE_SERVICE"

    override fun createWriteService(destination: String, kafkaProperties: Properties): ConfigWriteService {
        val publisher = publisherFactory.createPublisher(
            PublisherConfig(
                CONFIGURATION_WRITE_SERVICE
            ),
            ConfigFactory.parseProperties(kafkaProperties)
        )
        return ConfigWriteServiceImpl(destination, publisher)
    }
}
