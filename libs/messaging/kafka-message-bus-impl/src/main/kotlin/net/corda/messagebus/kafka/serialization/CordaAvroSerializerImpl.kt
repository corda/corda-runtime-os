package net.corda.messagebus.kafka.serialization

import net.corda.data.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.uncheckedCast
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

class CordaAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : CordaAvroSerializer<T>, Serializer<T> {

    companion object {
        private val stringSerializer = StringSerializer()
    }

    override fun serialize(data: T): ByteArray? {
        return when (data.javaClass) {
            String::class.java -> {
                stringSerializer.serialize(null, uncheckedCast(data))
            }
            ByteArray::class.java -> {
                uncheckedCast(data)
            }
            else -> {
                schemaRegistry.serialize(data).array()
            }
        }
    }

    override fun serialize(topic: String?, data: T?): ByteArray? {
        return when (data) {
            null -> null
            else -> serialize(data)
        }
    }
}
