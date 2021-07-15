package net.corda.messaging.kafka.subscription

import net.corda.messaging.kafka.publisher.CordaAvroSerializer.Companion.BYTE_ARRAY_MAGIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer.Companion.STRING_MAGIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.nio.ByteBuffer

class CordaAvroDeserializer<T>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: (String, ByteArray) -> Unit,
) : Deserializer<T> {

    private companion object {
        val BYTE_ARRAY_MAGIC_BYTES = BYTE_ARRAY_MAGIC.toByteArray()
        val stringSerializer = StringSerializer()
        val stringDeserializer = StringDeserializer()
    }

    override fun deserialize(topic: String, data: ByteArray?): T? {
        val stringMagicSerialized = stringSerializer.serialize(topic, STRING_MAGIC)

        when {
            data == null -> {
                return null
            }
            startsWith(data, stringMagicSerialized) -> {
                return uncheckedCast(stringDeserializer.deserialize(topic, data.copyOfRange(stringMagicSerialized.size, data.size - 1)))
            }
            startsWith(data, BYTE_ARRAY_MAGIC_BYTES) -> {
                return uncheckedCast(data.copyOfRange(BYTE_ARRAY_MAGIC_BYTES.size, data.size - 1))
            }
            else -> {
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
    }

    /**
     * Compare two byte arrays to see one is the prefix of another
     */
    private fun startsWith(array: ByteArray?, prefix: ByteArray?): Boolean {
        if (array.contentEquals(prefix)) {
            return true
        }
        if (array == null || prefix == null) {
            return false
        }
        val prefixLength = prefix.size
        if (prefix.size > array.size) {
            return false
        }
        for (i in 0 until prefixLength) {
            if (array[i] != prefix[i]) {
                return false
            }
        }
        return true
    }
}
