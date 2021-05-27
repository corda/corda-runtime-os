package net.corda.libs.configuration.write.factory

import net.corda.data.config.Configuration
import net.corda.libs.configuration.write.CordaWriteService
import net.corda.libs.configuration.write.CordaWriteServiceImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.publisher.factory.createPublisher
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation for [CordaWriteServiceFactory].
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaWriteServiceFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CordaWriteServiceFactory {

    private val CONFIGURATION_WRITE_SERVICE = "CONFIGURATION_WRITE_SERVICE"

    override fun createWriteService(destination: String): CordaWriteService {
        val publisher = publisherFactory.createPublisher<String, Configuration>(
            PublisherConfig(
                CONFIGURATION_WRITE_SERVICE,
                destination
            ), mapOf()
        )
        return CordaWriteServiceImpl(destination, publisher)
    }
}