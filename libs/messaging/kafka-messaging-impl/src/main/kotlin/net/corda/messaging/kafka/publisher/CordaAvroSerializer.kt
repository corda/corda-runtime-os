package net.corda.messaging.kafka.publisher

import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

class CordaAvroSerializer<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : Serializer<T> {

    companion object {
        private val stringSerializer = StringSerializer()
    }

    override fun serialize(topic: String?, data: T?): ByteArray? {
        return when {
            data == null -> {
                null
            }
            data.javaClass == String::class.java -> {
                stringSerializer.serialize(topic, data as String)
            }
            data.javaClass == ByteArray::class.java -> {
                data as ByteArray
            }
            else -> {
                schemaRegistry.serialize(data).array()
            }
        }
    }
}
