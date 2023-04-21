package net.corda.messagebus.db.serialization

import java.nio.ByteBuffer
import java.util.function.Consumer
import net.corda.data.CordaAvroDeserializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory

class CordaDBAvroDeserializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: Consumer<ByteArray>,
    private val expectedClass: Class<T>
) : CordaAvroDeserializer<T> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val errorMsg = "Failed to deserialized bytes returned"
    }

    private fun deserialize(data: ByteArray, allowChunks: Boolean = false): Any? {
        val isChunkType  = allowChunks && isChunkType(data)
        @Suppress("unchecked_cast")
        return when {
            expectedClass == String::class.java && !isChunkType -> {
                data.decodeToString() as T?
            }
            expectedClass == ByteArray::class.java && !isChunkType -> {
                data
            }
            else -> {
                deserializeAvro(data, allowChunks)
            }
        }
    }

    @Suppress("ComplexCondition")
    private fun deserializeAvro(data: ByteArray, allowChunks: Boolean) : Any? = try {
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
        // We don't want to throw back into Kafka as that would mean the entire poll (with possibly
        // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
        // and return a null.  This will mean the record gets treated as 'deleted' in the processors
        onError.accept(data)
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

    @Suppress("unchecked_cast")
    override fun deserialize(data: ByteArray): T? {
        return deserialize(data, false) as T?
    }
}
