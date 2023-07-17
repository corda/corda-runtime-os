package net.corda.testing.driver.serialization

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.testing.driver.sandbox.DRIVER_SERVICE
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_RANKING
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

/**
 * Implementation of the Serialization Factory.
 */
@Suppress("unused")
@Component(property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class CordaAvroSerializationFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
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

    override fun <T : Any> createAvroSerializer(
        onError: ((ByteArray) -> Unit)?
    ): CordaAvroSerializer<T> {
        return CordaAvroSerializerImpl(avroSchemaRegistry, onError)
    }
}
