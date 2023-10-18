package net.corda.messagebus.kafka.serialization

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Corda avro serializer impl
 *
 * @param T Type to deserialize
 * @property schemaRegistry the Avro-based Schemas
 * @property onError lambda to be run on deserialization error
 * @property expectedClass
 */
class CordaAvroDeserializerImpl<T: Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: (ByteArray, String?) -> Unit,
    private val expectedClass: Class<T>
) : CordaAvroDeserializer<T>, Deserializer<Any> {

    private companion object {
        val stringDeserializer = StringDeserializer()
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val errorMsg = "Failed to deserialize bytes returned"
    }

    private fun deserialize(data: ByteArray, allowChunks: Boolean = false, topic: String? = null): Any? {
        val isChunkType  = allowChunks && isChunkType(data)
        @Suppress("unchecked_cast")
        return when {
            expectedClass == String::class.java && !isChunkType -> {
                stringDeserializer.deserialize(null, data)
            }
            expectedClass == ByteArray::class.java && !isChunkType -> {
                data
            }
            else -> {
                deserializeAvro(data, allowChunks, topic)
            }
        }
    }

    @Suppress("ComplexCondition")
    private fun deserializeAvro(data: ByteArray, allowChunks: Boolean, topic: String?) : Any? = try {
        val dataBuffer = ByteBuffer.wrap(data)
        val clazz = schemaRegistry.getClassType(dataBuffer)

        if (expectedClass == clazz
            || expectedClass == Any::class.java
            || (allowChunks && (clazz == Chunk::class.java || clazz == ChunkKey::class.java))
        ) {
            schemaRegistry.deserialize(dataBuffer, clazz, null)
        } else {
            val msg = "$errorMsg. Found class: ${clazz.name}, expected class: $expectedClass, AllowChunks: $allowChunks"
            throw CordaRuntimeException(msg)
        }
    } catch (ex: Throwable) {
        log.warn("$errorMsg. Expected class: $expectedClass, AllowChunks: $allowChunks", ex)
        // We don't want to throw as that would mean the entire poll (with possibly
        // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
        // and return a null.  This will mean the record gets treated as 'deleted' in the processors
        onError.invoke(data, topic)
        null
    }

    private fun isChunkType(bytes: ByteArray): Boolean {
        return try {
            val clazz = schemaRegistry.getClassType(ByteBuffer.wrap(bytes))
            clazz == Chunk::class.java || clazz == ChunkKey::class.java
        } catch (ex: Exception) {
            //swallow exception and do not log as this will be very common for AMQP serialized byte payloads
            false
        }
    }

    override fun deserialize(data: ByteArray): T? {
        @Suppress("unchecked_cast")
        return deserialize(data, false) as? T
    }

    override fun deserialize(topic: String?, data: ByteArray?): Any? {
        return when(data) {
            null -> null
            else -> deserialize(data, true)
        }
    }
}
