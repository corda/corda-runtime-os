package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.ByteBufferInputStream
import net.corda.internal.serialization.CordaSerializationEncoding
import net.corda.internal.serialization.NullEncodingAllowList
import net.corda.internal.serialization.SectionId
import net.corda.internal.serialization.encodingNotPermittedFormat
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.serialization.EncodingAllowList
import net.corda.serialization.SerializationContext
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.util.trace
import net.corda.v5.serialization.SerializedBytes
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.codec.Data
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.ByteBuffer

data class ObjectAndEnvelope<out T>(val obj: T, val envelope: Envelope)

/**
 * Main entry point for deserializing an AMQP encoded object.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
class DeserializationInput constructor(
    private val serializerFactory: SerializerFactory
) {
    private val objectHistory: MutableList<Any> = mutableListOf()
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        @VisibleForTesting
        @Throws(AMQPNoTypeNotSerializableException::class)
        fun <T> withDataBytes(
            byteSequence: ByteSequence,
            encodingAllowList: EncodingAllowList,
            task: (ByteBuffer) -> T
        ): T {
            // Check that the lead bytes match expected header
            val amqpSequence = amqpMagic.consume(byteSequence)
                ?: throw AMQPNoTypeNotSerializableException("Serialization header does not match.")
            var stream: InputStream = ByteBufferInputStream(amqpSequence)
            try {
                while (true) {
                    when (SectionId.reader.readFrom(stream)) {
                        SectionId.ENCODING -> {
                            val encoding = CordaSerializationEncoding.reader.readFrom(stream)
                            encodingAllowList.acceptEncoding(encoding) ||
                                throw AMQPNoTypeNotSerializableException(encodingNotPermittedFormat.format(encoding))
                            stream = encoding.decompress(stream)
                        }
                        SectionId.DATA_AND_STOP, SectionId.ALT_DATA_AND_STOP -> return task(stream.asByteBuffer())
                    }
                }
            } finally {
                stream.close()
            }
        }

        @Throws(AMQPNoTypeNotSerializableException::class)
        fun getEnvelope(byteSequence: ByteSequence, encodingAllowList: EncodingAllowList = NullEncodingAllowList): Envelope {
            return withDataBytes(byteSequence, encodingAllowList) { dataBytes ->
                val data = Data.Factory.create()
                val expectedSize = dataBytes.remaining()
                if (data.decode(dataBytes) != expectedSize.toLong()) {
                    throw AMQPNoTypeNotSerializableException(
                        "Unexpected size of data",
                        "Blob is corrupted!."
                    )
                }
                Envelope.get(data)
            }
        }
    }

    @VisibleForTesting
    @Throws(AMQPNoTypeNotSerializableException::class)
    fun getEnvelope(byteSequence: ByteSequence, context: SerializationContext) = getEnvelope(byteSequence, context.encodingAllowList)

    @Throws(
        AMQPNotSerializableException::class,
        AMQPNoTypeNotSerializableException::class
    )
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>, context: SerializationContext): T =
        deserialize(bytes, T::class.java, context)

    @Throws(
        AMQPNotSerializableException::class,
        AMQPNoTypeNotSerializableException::class
    )
    @Suppress("ThrowsCount")
    private fun <R> des(generator: () -> R): R {
        try {
            return generator()
        } catch (amqp: AMQPNotSerializableException) {
            amqp.log("Deserialize", logger)
            throw NotSerializableException(amqp.mitigation).apply { initCause(amqp) }
        } catch (nse: NotSerializableException) {
            throw nse
        } catch (e: Exception) {
            throw NotSerializableException("Internal deserialization failure: ${e.javaClass.name}: ${e.message}").apply { initCause(e) }
        } finally {
            objectHistory.clear()
        }
    }

    /**
     * This is the main entry point for deserialization of AMQP payloads, and expects a byte sequence involving a header
     * indicating what version of Corda serialization was used, followed by an [Envelope] which carries the object to
     * be deserialized and a schema describing the types of the objects.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationContext): T =
        des {
            val envelope = getEnvelope(bytes, context.encodingAllowList)

            logger.trace { "deserialize blob scheme=\"${envelope.schema}\"" }

            doReadObject(envelope, clazz, context)
        }

    @Throws(NotSerializableException::class)
    fun <T : Any> deserializeAndReturnEnvelope(
        bytes: SerializedBytes<T>,
        clazz: Class<T>,
        context: SerializationContext
    ): ObjectAndEnvelope<T> = des {
        val envelope = getEnvelope(bytes, context.encodingAllowList)
        // Now pick out the obj and schema from the envelope.
        ObjectAndEnvelope(doReadObject(envelope, clazz, context), envelope)
    }

    private fun <T : Any> doReadObject(envelope: Envelope, clazz: Class<T>, context: SerializationContext): T {
        return clazz.cast(
            readObjectOrNull(
                obj = redescribe(envelope.obj, clazz),
                serializationSchemas = SerializationSchemas(envelope.schema, envelope.transformsSchema),
                metadata = envelope.metadata,
                type = clazz,
                context = context
            )
        )
    }

    fun readObjectOrNull(
        obj: Any?,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        type: Type,
        context: SerializationContext
    ): Any? {
        return if (obj == null) null else readObject(obj, serializationSchemas, metadata, type, context)
    }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        type: Type,
        context: SerializationContext
    ): Any =
        if (obj is DescribedType && ReferencedObject.DESCRIPTOR == obj.descriptor) {
            // It must be a reference to an instance that has already been read, cheaply and quickly returning it by reference.
            val objectIndex = (obj.described as UnsignedInteger).toInt()
            if (objectIndex >= objectHistory.size)
                throw AMQPNotSerializableException(
                    type,
                    "Retrieval of existing reference failed. Requested index $objectIndex " +
                        "is outside of the bounds for the list of size: ${objectHistory.size}"
                )

            val objectRetrieved = objectHistory[objectIndex]
            if (!objectRetrieved::class.java.isSubClassOf(type.asClass())) {
                throw AMQPNotSerializableException(
                    type,
                    "Existing reference type mismatch. Expected: '$type', found: '${objectRetrieved::class.java}' " +
                        "@ $objectIndex"
                )
            }
            objectRetrieved
        } else {
            val sandboxGroup = context.currentSandboxGroup()
            val objectRead = when (obj) {
                is DescribedType -> {
                    // Look up serializer in factory by descriptor
                    val serializer = serializerFactory.get(obj.descriptor.toString(), serializationSchemas, metadata, sandboxGroup)
                    if (type != TypeIdentifier.UnknownType.getLocalType(sandboxGroup) && serializer.type != type && with(serializer.type) {
                        !isSubClassOf(type) && !materiallyEquivalentTo(type)
                    }
                    ) {
                        throw AMQPNotSerializableException(
                            type,
                            "Described type with descriptor ${obj.descriptor} was " +
                                "expected to be of type $type but was ${serializer.type}"
                        )
                    }
                    serializer.readObject(obj.described, serializationSchemas, metadata, this, context)
                }
                is Binary -> obj.array
                else -> if ((type is Class<*>) && type.isPrimitive) {
                    // this will be the case for primitive types like [boolean] et al.
                    obj
                } else {
                    // these will be boxed primitive types
                    serializerFactory.get(obj::class.java, type).readObject(obj, serializationSchemas, metadata, this, context)
                }
            }

            // Store the reference in case we need it later on.
            // Skip for primitive types as they are too small and overhead of referencing them will be much higher
            // than their content
            if (serializerFactory.isSuitableForObjectReference(objectRead.javaClass)) {
                objectHistory.add(objectRead)
            }
            objectRead
        }

    /**
     * Currently performs checks aimed at:
     *  * [java.util.List<Command<?>>] and [java.lang.Class<? extends net.corda.core.contracts.Contract>]
     *  * [T : Parent] and [Parent]
     *  * [? extends Parent] and [Parent]
     *
     * In the future tighter control might be needed
     */
    private fun Type.materiallyEquivalentTo(that: Type): Boolean =
        when (that) {
            is ParameterizedType -> asClass() == that.asClass()
            is TypeVariable<*> -> isSubClassOf(that.bounds.first())
            is WildcardType -> isSubClassOf(that.upperBounds.first())
            else -> false
        }
}
