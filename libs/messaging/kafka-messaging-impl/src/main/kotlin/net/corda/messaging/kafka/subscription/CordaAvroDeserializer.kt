package net.corda.messaging.kafka.subscription

import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.nio.ByteBuffer

class CordaAvroDeserializer<T>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: (String, ByteArray) -> Unit,
    private val expectedClass: Class<T>
) : Deserializer<T> {

    private companion object {
        val stringDeserializer = StringDeserializer()
        val log = contextLogger()
    }

    override fun deserialize(topic: String, data: ByteArray?): T? {

        when {
            data == null -> {
                return null
            }
            expectedClass == String::class.java -> {
                return uncheckedCast(stringDeserializer.deserialize(topic, data))
            }
            expectedClass == ByteArray::class.java  -> {
                return uncheckedCast(data)
            }
            else -> {
                return try {
                    val dataBuffer = ByteBuffer.wrap(data)
                    val classType = schemaRegistry.getClassType(dataBuffer)
                    uncheckedCast(schemaRegistry.deserialize(dataBuffer, classType, null))
                } catch (ex: Throwable) {
                    log.error("Failed to deserialise to expected class $expectedClass")
                    /* We don't want to throw back into Kafka as that would mean the entire poll (with possibly
                 * many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
                 * and return a null.  This will mean the record gets treated as 'deleted' in the processors
                 */
                    onError.invoke(topic, data)
                    null
                }
            }
        }
    }
}
