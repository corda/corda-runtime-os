package net.corda.messagebus.kafka.serialization


import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CordaKafkaSerializationFactory::class])
class CordaKafkaSerializationFactoryImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
): CordaKafkaSerializationFactory {
    override fun <T : Any> createAvroBasedKafkaDeserializer(
        onError: ((ByteArray) -> Unit),
        expectedClass: Class<T>
    ): (Any?, ByteArray?) -> T? {
        val avroDeserializer = CordaAvroDeserializerImpl(avroSchemaRegistry, onError, expectedClass)
        return { _, d -> when (d) {
            null -> null
            else -> avroDeserializer.deserialize(d) } }
    }

    override fun createAvroBasedKafkaSerializer(
        onError: ((ByteArray) -> Unit)?
    ): (Any?, Any?) -> ByteArray? {
        val avroSerializer = CordaAvroSerializerImpl<Any>(avroSchemaRegistry, onError)
        return { _, d: Any? ->
            when (d) {
                null -> null
                else -> avroSerializer.serialize(d)
            }
        }
    }
}