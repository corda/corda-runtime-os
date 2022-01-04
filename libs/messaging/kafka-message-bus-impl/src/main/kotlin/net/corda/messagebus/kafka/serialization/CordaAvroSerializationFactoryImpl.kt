package net.corda.messagebus.kafka.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation of the Subscription Factory.
 */
@Component(service = [CordaAvroSerializationFactory::class])
class CordaAvroSerializationFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaAvroSerializationFactory {
    override fun <T : Any> createAvroDeserializer(
        onError: (ByteArray) -> Unit,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T> {
        return CordaAvroDeserializerImpl(
            avroSchemaRegistry,
            onError,
            expectedClass
        )
    }

    override fun <T: Any> createAvroSerializer(
        onError: (ByteArray) -> Unit
    ): CordaAvroSerializer<T> {
        return CordaAvroSerializerImpl(avroSchemaRegistry)
    }
}
