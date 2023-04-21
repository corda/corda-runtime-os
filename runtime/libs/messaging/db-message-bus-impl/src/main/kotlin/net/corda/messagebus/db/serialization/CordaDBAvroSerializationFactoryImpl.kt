package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.function.Consumer

/**
 * DB implementation of the Serialization Factory.
 */
@Component(service = [CordaAvroSerializationFactory::class])
class CordaDBAvroSerializationFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaAvroSerializationFactory {
    override fun <T : Any> createAvroDeserializer(
        onError: Consumer<ByteArray>,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T> {
        return CordaDBAvroDeserializerImpl(
            avroSchemaRegistry,
            onError,
            expectedClass
        )
    }

    override fun <T: Any> createAvroSerializer(
        onError: Consumer<ByteArray>
    ): CordaAvroSerializer<T> {
        return CordaDBAvroSerializerImpl(avroSchemaRegistry)
    }
}
