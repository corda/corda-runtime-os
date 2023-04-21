package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.SerializerFor
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.DESCRIPTOR_DOMAIN
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.typeDescriptorFor
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.withList
import net.corda.internal.serialization.model.FingerprintWriter
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer
import net.corda.serialization.InternalProxySerializer
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T : Any> : AMQPSerializer<T>, SerializerFor {
    protected abstract val descriptor: Descriptor

    final override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    ) {
        data.withDescribed(descriptor) {
            @Suppress("unchecked_cast")
            writeDescribedObject(obj as T, data, type, output, context)
        }
    }

    abstract fun writeDescribedObject(
        obj: T,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext
    )

    /**
     * This custom serializer represents a sort of symbolic link from a subclass to a super class, where the super
     * class custom serializer is responsible for the "on the wire" format but we want to create a reference to the
     * subclass in the schema, so that we can distinguish between subclasses.
     */
    class SubClass<T : Any>(
        clazz: Class<*>,
        private val superClassSerializer: CustomSerializer<T>
    ) : CustomSerializer<T>() {
        override val revealSubclassesInSchema: Boolean
            get() = false

        override val type: Class<*> = clazz

        override val typeDescriptor: Symbol

        init {
            val fingerprint = FingerprintWriter()
                .write(superClassSerializer.typeDescriptor)
                .write(AMQPTypeIdentifiers.nameForType(clazz))
                .fingerprint
            typeDescriptor = Symbol.valueOf("$DESCRIPTOR_DOMAIN:$fingerprint")
        }

        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        private val typeNotation: TypeNotation = RestrictedType(
            AMQPTypeIdentifiers.nameForType(clazz),
            null,
            emptyList(),
            AMQPTypeIdentifiers.nameForType(superClassSerializer.type),
            Descriptor(typeDescriptor),
            emptyList()
        )

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz === type

        override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
            output.writeTypeNotations(typeNotation)
        }

        override fun writeDescribedObject(
            obj: T,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            superClassSerializer.writeDescribedObject(obj, data, type, output, context)
        }

        override fun readObject(
            obj: Any,
            serializationSchemas: SerializationSchemas,
            metadata: Metadata,
            input: DeserializationInput,
            context: SerializationContext
        ): T {
            return superClassSerializer.readObject(obj, serializationSchemas, metadata, input, context)
        }

        override fun toString(): String = "${this::class.java.simpleName}(${type.name}):$superClassSerializer"
    }

    /**
     * Additional base features for internal custom serializers.
     */
    abstract class CustomSerializerImpl<T : Any>(
        @JvmField
        protected val serializer: InternalCustomSerializer<T>
    ) : CustomSerializer<T>() {
        final override val revealSubclassesInSchema: Boolean
            get() = serializer.revealSubclasses

        final override val type: Class<*> = serializer.type
        final override val typeDescriptor: Symbol = typeDescriptorFor(type)
        final override val descriptor: Descriptor = Descriptor(typeDescriptor)

        final override fun isSerializerFor(clazz: Class<*>): Boolean {
            return if (serializer.withInheritance) {
                type.isAssignableFrom(clazz)
            } else {
                type === clazz
            }
        }

        override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}

        override fun toString(): String = "${this::class.java.simpleName}(${serializer::class.java.name})"
    }

    class Direct<T : Any>(
        serializer: InternalDirectSerializer<T>
    ) : CustomSerializerImpl<T>(serializer) {
        override fun writeDescribedObject(
            obj: T,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            (serializer as InternalDirectSerializer<T>).writeObject(obj, WriteDataObject(data), context)
        }

        override fun readObject(
            obj: Any,
            serializationSchemas: SerializationSchemas,
            metadata: Metadata,
            input: DeserializationInput,
            context: SerializationContext
        ): T {
            return (serializer as InternalDirectSerializer<T>).readObject(ReadDataObject(obj), context)
        }
    }

    private class WriteDataObject(private val data: Data) : InternalDirectSerializer.WriteObject {
        override fun putAsBytes(value: ByteArray) = data.putBinary(value)
        override fun putAsString(value: String) = data.putString(value)
        override fun putAsObject(value: Any) = data.putObject(value)
    }

    private class ReadDataObject(private val obj: Any) : InternalDirectSerializer.ReadObject {
        override fun getAsBytes(): ByteArray = (obj as Binary).array
        override fun <T: Any> getAs(type: Class<T>): T = type.cast(obj)
    }

    /**
     * Additional base features over and above [Direct] custom serializers for when the serialized form should be
     * the serialized form of a proxy class, and the object can be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    @Suppress("unchecked_cast")
    class Proxy<T : Any, P : Any>(
        serializer: InternalProxySerializer<T, P>,
        factory: SerializerFactory
    ) : CustomSerializerImpl<T>(serializer) {
        private val proxySerializer: ObjectSerializer by lazy(LazyThreadSafetyMode.PUBLICATION) {
            ObjectSerializer.make(factory.getTypeInformation(serializer.proxyType), factory)
        }

        override fun writeDescribedObject(
            obj: T,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            val proxy = (serializer as InternalProxySerializer<T, P>).toProxy(obj, context)
            data.withList {
                proxySerializer.propertySerializers.forEach { (_, serializer) ->
                    serializer.writeProperty(proxy, this, output, context, 0)
                }
            }
        }

        override fun readObject(
            obj: Any,
            serializationSchemas: SerializationSchemas,
            metadata: Metadata,
            input: DeserializationInput,
            context: SerializationContext
        ): T {
            val proxy = proxySerializer.readObject(obj, serializationSchemas, metadata, input, context) as P
            return (serializer as InternalProxySerializer<T, P>).fromProxy(proxy, context)
        }
    }
}
