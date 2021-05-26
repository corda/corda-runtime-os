package net.corda.libs.configuration.write.factory

import net.corda.data.config.Configuration
import net.corda.libs.configuration.write.CordaWriteService
import net.corda.libs.configuration.write.CordaWriteServiceImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.createPublisher
import net.corda.messaging.kafka.publisher.factory.CordaKafkaPublisherFactory
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation for [CordaWriteServiceFactory].
 * @property avroSchemaRegistry OSGi DS Injected avro schema registry
 */
@Component
class CordaWriteServiceFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : CordaWriteServiceFactory {

    private val cordaKafkaPublisherFactory = CordaKafkaPublisherFactory(avroSchemaRegistry)


    override fun getWriteService(destination: String): CordaWriteService {
        val publisher = cordaKafkaPublisherFactory.createPublisher<String, Configuration>(
            PublisherConfig(
                "",
                destination
            ), mapOf()
        )
        return CordaWriteServiceImpl(destination, publisher)
    }
}