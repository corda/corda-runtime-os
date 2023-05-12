package net.corda.internal.serialization.amqp

import net.corda.base.internal.ByteSequence
import net.corda.internal.serialization.ByteBufferInputStream
import net.corda.internal.serialization.CordaSerializationEncoding
import net.corda.internal.serialization.NullEncodingAllowList
import net.corda.internal.serialization.SectionId
import net.corda.internal.serialization.encodingNotPermittedFormat
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.internal.serialization.unwrap
import net.corda.serialization.EncodingAllowList
import net.corda.serialization.SerializationContext
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.trace
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
        deserialize(bytes.unwrap(), T::class.java, context)

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
        val envelope = getEnvelope(bytes.unwrap(), context.encodingAllowList)
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

    /**
     * Reads and handles the deserialization of an object.
     *
     * This function receives an object [obj] and delegates its deserialization to the correct handler.
     * If the object is suitable for object reference (not a primitive type), it is stored in the object history.
     * The resulting object is returned.
     *
     * @param obj The object to be serialized.
     * @param serializationSchemas The serialization schemas.
     * @param metadata The metadata for serialization.
     * @param type The expected type of the object.
     * @param context The serialization context.
     * @return The serialized object.
     */
    fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        type: Type,
        context: SerializationContext
    ): Any {
        return if (obj is DescribedType && ReferencedObject.DESCRIPTOR == obj.descriptor) {
            handleReferencedObject(obj, type)
        } else {
            val objectRead = when (obj) {
                is DescribedType -> handleDescribedType(obj, serializationSchemas, metadata, type, context)
                is Binary -> obj.array
                else -> handlePrimitiveTypes(obj, serializationSchemas, metadata, type, context)
            }

            // Store the reference in case we need it later on. Skip for primitive types as they are
            // too small and overhead of referencing them will be much higher than their content
            if (serializerFactory.isSuitableForObjectReference(objectRead.javaClass)) {
                objectHistory.add(objectRead)
            }

            objectRead
        }
    }

    /**
     * Handles the deserialization of a referenced object.
     *
     * This function receives a described type object [obj] representing a reference to an already read instance.
     * We retrieve the [objectIndex] from [obj] to determine the reference to be retrieved. If the index is within
     * the bounds of the object history, retrieve it, deserialize it, and return it.
     *
     * If the index is not in bounds, or the class of the retrieved object does not match the expected [type],
     * throw an [AMQPNotSerializableException].
     *
     * @param obj The described type object representing the reference.
     * @param type The expected type of the referenced object.
     * @return The referenced object.
     * @throws AMQPNotSerializableException if there is an issue with serialization.
     */
    private fun handleReferencedObject(obj: DescribedType, type: Type): Any {
        // It must be a reference to an instance that has already been read, cheaply and quickly returning it by reference.
        val objectIndex = (obj.described as UnsignedInteger).toInt()
        if (objectIndex >= objectHistory.size) {
            throw AMQPNotSerializableException(
                type,
                "Retrieval of existing reference failed. Requested index $objectIndex " +
                        "is outside of the bounds for the list of size: ${objectHistory.size}"
            )
        }

        val objectRetrieved = objectHistory[objectIndex]
        if (!objectRetrieved::class.java.isSubClassOf(type.asClass())) {
            throw AMQPNotSerializableException(
                type,
                "Existing reference type mismatch. Expected: '$type', found: '${objectRetrieved::class.java}' " +
                        "@ $objectIndex"
            )
        }

        return objectRetrieved
    }

    /**
     * Handles the deserialization of described type objects.
     *
     * Retrieves a serializer from the [SerializerFactory] based on the descriptor of [obj]. If the serializer type
     * matches the expected [type], we deserialize and return the object. Otherwise, an [AMQPNotSerializableException]
     * is thrown.
     *
     * @param obj The described type object to be serialized.
     * @param serializationSchemas The serialization schemas to be used.
     * @param metadata The metadata associated with the serialization process.
     * @param type The expected type of the object.
     * @param context The serialization context.
     * @return The deserialized object.
     * @throws AMQPNotSerializableException
     */
    private fun handleDescribedType(
        obj: DescribedType,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        type: Type,
        context: SerializationContext
    ): Any {
        // Look up serializer in factory by descriptor
        val serializer = serializerFactory.get(obj.descriptor.toString(), serializationSchemas, metadata, context.currentSandboxGroup())
        if (type != TypeIdentifier.UnknownType.getLocalType(context.currentSandboxGroup()) &&
            serializer.type != type && !serializer.type.isSubClassOf(type) && !serializer.type.materiallyEquivalentTo(type)
        ) {
            throw AMQPNotSerializableException(
                type,
                "Described type with descriptor ${obj.descriptor} was " +
                        "expected to be of type $type but was ${serializer.type}"
            )
        }

        return serializer.readObject(obj.described, serializationSchemas, metadata, this, context)
    }

    /**
     * Handles the deserialization of primitive types.
     *
     * If the type is primitive, the object is returned as is. Otherwise, we delegate to the appropriate serializer
     * based on the object's class and the specified type. Special narrowing conversion is added for [Character] type.
     *
     * @param obj The object to be serialized.
     * @param serializationSchemas The serialization schemas to be used.
     * @param metadata The metadata associated with the serialization process.
     * @param type The type of the object.
     * @param context The serialization context.
     * @return The deserialized object.
     * @throws AMQPNotSerializableException
     */
    private fun handlePrimitiveTypes(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        type: Type,
        context: SerializationContext
    ): Any {
        return if (type is Class<*> && type.isPrimitive) {
            obj // this will be the case for primitive types like [boolean] et al.
        } else {
            // Special handling needed as [Char] is widened to an [Integer] in transit to allow negative values for EOF characters
            // See: https://qpid.apache.org/releases/qpid-proton-j-0.33.8/api/org/apache/qpid/proton/codec/Data.html#putChar-int-
            val actualType = if (type == Character::class.java && obj is Int) obj.toChar() else obj

            // these will be boxed primitive types
            serializerFactory.get(obj::class.java, type)
                .readObject(actualType, serializationSchemas, metadata, this, context)
        }
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
