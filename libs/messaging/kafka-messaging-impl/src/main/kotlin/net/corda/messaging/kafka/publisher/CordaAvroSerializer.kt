package net.corda.messaging.kafka.publisher

import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.common.serialization.Serializer

class CordaAvroSerializer<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return schemaRegistry.serialize(data).array()
    }
}
