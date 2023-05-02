package net.corda.messagebus.kafka.serialization

import java.util.function.Consumer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.serialization.CordaAvroDeserializer
import net.corda.serialization.CordaAvroSerializationFactory
import net.corda.serialization.CordaAvroSerializer
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
        onError: Consumer<ByteArray>,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T> {
        return CordaAvroDeserializerImpl(
            avroSchemaRegistry,
            onError,
            expectedClass
        )
    }

    override fun <T : Any> createAvroSerializer(
        throwOnError: Boolean,
        onError: Consumer<ByteArray>?
    ): CordaAvroSerializer<T> {
        return CordaAvroSerializerImpl(avroSchemaRegistry, throwOnError, onError)
    }
}
