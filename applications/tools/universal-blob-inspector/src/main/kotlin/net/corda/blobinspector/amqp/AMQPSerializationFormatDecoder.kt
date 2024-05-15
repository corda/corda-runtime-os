package net.corda.blobinspector.amqp

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.blobinspector.ByteSequence
import net.corda.blobinspector.DecodedBytes
import net.corda.blobinspector.Encoding
import net.corda.blobinspector.SerializationFormatDecoder
import net.corda.blobinspector.readFully
import net.corda.blobinspector.sequence
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.codec.Data
import java.io.InputStream
import java.nio.ByteBuffer

class AMQPSerializationFormatDecoder(
    val recurse: (ByteSequence, Int, Boolean) -> Any?,
    val envelopeFactory: (Data) -> Envelope,
    val registryFactory: () -> DynamicDescriptorRegistry
) : SerializationFormatDecoder {
    override fun duplicate(): SerializationFormatDecoder {
        return AMQPSerializationFormatDecoder(recurse, envelopeFactory, registryFactory)
    }

    private val referencedObjects = mutableListOf<Any?>()
    override fun decode(
        stream: InputStream,
        recurseDepth: Int,
        originalBytes: ByteArray,
        includeOriginalBytes: Boolean,
        hasHeader: Boolean
    ): DecodedBytes {
        val dataBytes: ByteBuffer = ByteBuffer.wrap(stream.readFully())
        val data = Data.Factory.create()
        val expectedSize = dataBytes.remaining()
        if (data.decode(dataBytes) != expectedSize.toLong()) {
            println("Blob includes tail padding")
        }
        val envelope = envelopeFactory(data)
        // println("Object = \n${envelope.obj}")
        // println("Schema = \n${envelope.schema}")
        val registry = registryFactory()
        registerSchema(registry, envelope.schema)

        val rendering = mutableMapOf(
            "_value" to readObject(envelope.obj, registry, recurseDepth, false, includeOriginalBytes),

        )
        if (includeOriginalBytes) {
            rendering["_bytes"] = originalBytes
        }

        return AMQPDecodedBytes(rendering)
    }

    private fun readObject(
        obj: Any?,
        registry: DynamicDescriptorRegistry,
        depth: Int,
        described: Boolean,
        includeOriginalBytes: Boolean
    ): Any? {
        return if (obj is DescribedType) {
            val descriptor = if (obj.descriptor is Symbol) {
                Descriptor(obj.descriptor as Symbol, null)
            } else if (obj.descriptor is UnsignedLong) {
                Descriptor(null, obj.descriptor as UnsignedLong)
            } else {
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("Described type descriptor is unexpectedly ${obj.descriptor}")
            }
            val typeHandle = registry[descriptor] ?: run {
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("No registered type for descriptor $descriptor")
            }

            val transformedObj = readObject(obj.described, registry, depth + 1, true, includeOriginalBytes)
            val transformed = typeHandle.transform(transformedObj, referencedObjects)
            val serializedBytes = extractSerializedBytes(transformed)
            (
                if (serializedBytes != null) {
                    mapOf(
                        "_class" to (transformed as Map<*, *>)["_class"],
                        "_value" to recurse(serializedBytes, depth + 1, includeOriginalBytes)
                    )
                } else {
                    transformed
                }
                ).also {
                referencedObjects.add(it)
            }
        } else if (obj is List<*>) {
            val list = obj as List<Any?>
            return list.map { readObject(it, registry, depth + 1, false, includeOriginalBytes) }.also {
                if (!described) referencedObjects.add(it)
            }
        } else if (obj is Map<*, *>) {
            return obj.map {
                readObject(it.key, registry, depth + 1, false, includeOriginalBytes) to readObject(
                    it.value,
                    registry,
                    depth + 1,
                    false,
                    includeOriginalBytes
                )
            }.toMap().also {
                if (!described) referencedObjects.add(it)
            }
        } else if (obj is Binary) {
            val bytes = obj.array.sequence()
            if (bytes.size > 5 && bytes.subSequence(0, 5) == Encoding.cordaMagic) {
                try {
                    return recurse(bytes, depth + 1, includeOriginalBytes)
                } catch (e: Exception) {
                    // Ignore
                }
            } else {
                // See if we can parse as JSON
                try {
                    return ObjectMapper().readValue(obj.array, Any::class.java)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return bytes
        } else if (obj is Array<*>) {
            return obj.map { readObject(it, registry, depth + 1, false, includeOriginalBytes) }.toTypedArray().also {
                if (!described) referencedObjects.add(it)
            }
        } else {
            return obj
        }
    }

    private fun isC4SerializedBytes(any: Map<*, *>): Boolean {
        return (any["_class"] as? String)?.startsWith("net.corda.core.serialization.SerializedBytes") ?: false
    }

    private fun extractSerializedBytes(any: Any?): ByteSequence? {
        return if (any is Map<*, *> && isC4SerializedBytes(any)) any["bytes"] as? ByteSequence else null
    }

    private fun registerSchema(registry: DynamicDescriptorRegistry, schema: Schema) {
        for (type in schema.types) {
            registry.register(type)
        }
    }

    private class AMQPDecodedBytes(override val result: Any?) : DecodedBytes
}
