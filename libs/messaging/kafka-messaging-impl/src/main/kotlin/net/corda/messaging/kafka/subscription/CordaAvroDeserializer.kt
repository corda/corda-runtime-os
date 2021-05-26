package net.corda.messaging.kafka.subscription

import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.common.serialization.Deserializer
import java.nio.ByteBuffer

class CordaAvroDeserializer<T>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: (String, ByteArray) -> Unit,
) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray?): T? {
        if (data == null) {
            return null
        }

        return try {
            val dataBuffer = ByteBuffer.wrap(data)
            val classType = schemaRegistry.getClassType(dataBuffer)
            uncheckedCast(schemaRegistry.deserialize(dataBuffer, classType, null))
        } catch (ex: CordaRuntimeException) {
            /* We don't want to throw back into Kafka as that would mean the entire poll (with possibly
             * many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
             * and return a null.  This will mean the record gets treated as 'deleted' in the processors
             */
            onError.invoke(topic, data)
            null
        }
    }
}
