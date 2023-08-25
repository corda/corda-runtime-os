package net.corda.messagebus.db.serialization

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * DB implementation of the Serialization Factory.
 */
@Component(service = [CordaAvroSerializationFactory::class])
class CordaDBAvroSerializationFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaAvroSerializationFactory {
    override fun <T : Any> createAvroDeserializer(
        onError: (ByteArray) -> Unit,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T> {
        return CordaDBAvroDeserializerImpl(
            avroSchemaRegistry,
            onError,
            expectedClass
        )
    }

    override fun <T: Any> createAvroBasedKafkaDeserializer(
        onError: ((ByteArray) -> Unit),
        expectedClass: Class<T>
    ): (Any?, ByteArray?) -> T? {
        val avroDeserializer = CordaDBAvroDeserializerImpl(avroSchemaRegistry, onError, expectedClass)
        return { _, d -> when (d) {
            null -> null
            else -> avroDeserializer.deserialize(d) }}
    }

    override fun <T : Any> createAvroSerializer(
        onError: ((ByteArray) -> Unit)?
    ): CordaAvroSerializer<T> {
        return CordaDBAvroSerializerImpl(avroSchemaRegistry, onError)
    }

    override fun createAvroBasedKafkaSerializer(
        onError: ((ByteArray) -> Unit)?
    ): (Any?, Any?) -> ByteArray? {
        val avroSerializer = CordaDBAvroSerializerImpl<Any>(avroSchemaRegistry, onError)
        return { _, d ->
            when (d) {
                null -> null
                else -> avroSerializer.serialize(d)
            }
        }
    }
}
