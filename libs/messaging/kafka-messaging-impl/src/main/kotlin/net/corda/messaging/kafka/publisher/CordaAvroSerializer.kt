package net.corda.messaging.kafka.publisher

import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

class CordaAvroSerializer<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : Serializer<T> {

    companion object {
        const val STRING_MAGIC = "corda-string-"
        const val BYTE_ARRAY_MAGIC = "corda-bytearray-"
        private val stringSerializer = StringSerializer()
    }

    override fun serialize(topic: String?, data: T?): ByteArray? {
        return when {
            data == null -> {
                null
            }
            data.javaClass == String::class.java -> {
                stringSerializer.serialize(topic, STRING_MAGIC) + stringSerializer.serialize(topic, data as String)
            }
            data.javaClass == ByteArray::class.java -> {
                BYTE_ARRAY_MAGIC.toByteArray() + data as ByteArray
            }
            else -> {
                schemaRegistry.serialize(data).array()
            }
        }
    }
}
