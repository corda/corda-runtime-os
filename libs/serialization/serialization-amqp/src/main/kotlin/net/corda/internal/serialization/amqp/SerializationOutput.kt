package net.corda.internal.serialization.amqp

import net.corda.classinfo.ClassInfoException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializedBytes
import net.corda.internal.serialization.CordaSerializationEncoding
import net.corda.internal.serialization.SectionId
import net.corda.internal.serialization.byteArrayOutput
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.internal.serialization.osgi.TypeResolver
import net.corda.sandbox.CpkClassInfo
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.io.OutputStream
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.IdentityHashMap
import kotlin.collections.LinkedHashSet

data class BytesAndSchemas<T : Any>(
        val obj: SerializedBytes<T>,
        val schema: Schema,
        val transformsSchema: TransformsSchema,
        val metadata: Metadata)

/**
 * Main entry point for serializing an object to AMQP.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
open class SerializationOutput constructor(
        internal val serializerFactory: LocalSerializerFactory
) {
    companion object {
        private val logger = contextLogger()
    }

    private val objectHistory: MutableMap<Any, Int> = IdentityHashMap()
    private val serializerHistory: MutableSet<AMQPSerializer<*>> = LinkedHashSet()
    internal val schemaHistory: MutableSet<TypeNotation> = LinkedHashSet()
    private val metadata = Metadata()

    /**
     * Serialize the given object to AMQP, wrapped in our [Envelope] wrapper which carries an AMQP 1.0 schema, and prefixed
     * with a header to indicate that this is serialized with AMQP and not Kryo, and what version of the Corda implementation
     * of AMQP serialization constructed the serialized form.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        try {
            return _serialize(obj, context)
        } catch (amqp: AMQPNotSerializableException) {
            amqp.log("Serialize", logger)
            throw NotSerializableException(amqp.mitigation)
        } finally {
            andFinally()
        }
    }

    // NOTE: No need to handle AMQPNotSerializableExceptions here as this is an internal
    // only / testing function and it doesn't matter if they escape
    @Throws(NotSerializableException::class)
    fun <T : Any> serializeAndReturnSchema(obj: T, context: SerializationContext): BytesAndSchemas<T> {
        try {
            val blob = _serialize(obj, context)
            val schema = Schema(schemaHistory.toList())
            return BytesAndSchemas(blob, schema, TransformsSchema.build(schema, serializerFactory, context, metadata), Metadata())
        } finally {
            andFinally()
        }
    }

    protected fun andFinally() {
        objectHistory.clear()
        serializerHistory.clear()
        schemaHistory.clear()
        metadata.clear()
    }

    protected fun <T : Any> _serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val data = Data.Factory.create()
        data.withDescribed(Envelope.DESCRIPTOR_OBJECT) {
            withList {
                writeObject(obj, this, context)
                val schema = Schema(schemaHistory.toList())
                writeSchema(schema, this)
                writeTransformSchema(TransformsSchema.build(schema, serializerFactory, context, metadata), this)
                writeMetadata(metadata, this)
            }
        }
        return SerializedBytes(byteArrayOutput {
            var stream: OutputStream = it
            try {
                amqpMagic.writeTo(stream)
                val encoding = context.encoding
                if (encoding != null) {
                    SectionId.ENCODING.writeTo(stream)
                    (encoding as CordaSerializationEncoding).writeTo(stream)
                    stream = encoding.compress(stream)
                }
                SectionId.DATA_AND_STOP.writeTo(stream)
                stream.alsoAsByteBuffer(data.encodedSize().toInt(), data::encode)
            } finally {
                stream.close()
            }
        })
    }

    internal fun writeObject(obj: Any, data: Data, context: SerializationContext) {
        writeObject(obj, data, obj.javaClass, context)
    }

    open fun writeSchema(schema: Schema, data: Data) {
        data.putObject(schema)
    }

    open fun writeTransformSchema(transformsSchema: TransformsSchema, data: Data) {
        data.putObject(transformsSchema)
    }

    internal fun writeObjectOrNull(obj: Any?, data: Data, type: Type, context: SerializationContext, debugIndent: Int) {
        if (obj == null) {
            data.putNull()
        } else {
            writeObject(obj, data, if (type == TypeIdentifier.UnknownType.getLocalType()) obj.javaClass else type, context, debugIndent)
        }
    }

    open fun writeMetadata(metadata: Metadata, data: Data) {
        data.putObject(metadata)
    }

    /**
     * Attaches information about the CPKs associated with the serialised objects to the metadata
     */
    private fun writeTypeToMetadata(type: Type) {
        try {
            val classInfo = TypeResolver.getClassInfoFor(type.asClass())
            if (classInfo is CpkClassInfo && !metadata.containsKey(type.typeName)) {
                val key = type.typeName
                val value = listOf(
                        classInfo.classBundleName,
                        classInfo.classBundleVersion.toString(),
                        classInfo.cpkPublicKeyHashes.map(SecureHash::toString)
                )
                metadata.putValue(key, value)
            }
        } catch (ex: ClassInfoException) {
            logger.trace {
                "Class ${type.typeName} not found in any sandbox. " +
                "The type is either a PlatformClass or is not installed. "
            }
        } catch (ex: NullPointerException) {
            // This is likely a unit test
            logger.trace {
                "Cannot initialise classInfoService. " +
                "Unable to write metadata due to un-initialised OSGi service. " +
                        "This code runs outside an OSGi framework. ${ex.message}"
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun writeObject(obj: Any, data: Data, type: Type, context: SerializationContext, debugIndent: Int = 0) {
        val serializer = serializerFactory.get(obj.javaClass, type)
        if (serializer !in serializerHistory) {
            serializerHistory.add(serializer)
            serializer.writeClassInfo(this)
            writeTypeToMetadata(serializer.type)
        }

        val retrievedRefCount = objectHistory[obj]
        if (retrievedRefCount == null) {
            serializer.writeObject(obj, data, type, this, context, debugIndent)
            // Important to do it after serialization such that dependent object will have preceding reference numbers
            // assigned to them first as they will be first read from the stream on receiving end.
            // Skip for primitive types as they are too small and overhead of referencing them will be much higher than their content
            if (serializerFactory.isSuitableForObjectReference(obj.javaClass)) {
                objectHistory[obj] = objectHistory.size
            }
        } else {
            data.writeReferencedObject(ReferencedObject(retrievedRefCount))
        }
    }

    internal open fun writeTypeNotations(vararg typeNotation: TypeNotation): Boolean {
        return schemaHistory.addAll(typeNotation)
    }

    internal open fun requireSerializer(type: Type) {
        if (type != Object::class.java && type.typeName != "?") {
            val resolvedType = when(type) {
                is WildcardType ->
                    if (type.upperBounds.size == 1) type.upperBounds[0]
                    else throw NotSerializableException("Cannot obtain upper bound for type $type")
                else -> type
            }

            val serializer = serializerFactory.get(resolvedType)
            if (serializer !in serializerHistory) {
                serializerHistory.add(serializer)
                serializer.writeClassInfo(this)
                writeTypeToMetadata(serializer.type)
            }
        }
    }
}

