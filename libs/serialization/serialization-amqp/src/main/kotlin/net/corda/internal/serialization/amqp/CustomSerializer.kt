package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.FingerprintWriter
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
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
     * Additional base features for a custom serializer for a particular class [withInheritance] is false
     * or super class / interfaces [withInheritance] is true
     */
    abstract class CustomSerializerImpl<T : Any>(
        clazz: Class<T>,
        final override val revealSubclassesInSchema: Boolean,
        @JvmField
        protected val withInheritance: Boolean
    ) : CustomSerializer<T>() {
        final override val type: Class<*> = clazz
        final override val typeDescriptor: Symbol = typeDescriptorFor(clazz)
        final override val descriptor: Descriptor = Descriptor(typeDescriptor)

        final override fun isSerializerFor(clazz: Class<*>): Boolean {
            return if (withInheritance) {
                type.isAssignableFrom(clazz)
            } else {
                type === clazz
            }
        }

        override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}
    }

    /**
     * Additional base features for a custom serializer for a particular class, that excludes subclasses.
     */
    abstract class Is<T : Any>(clazz: Class<T>)
        : CustomSerializerImpl<T>(clazz, revealSubclassesInSchema = false, withInheritance = false)

    /**
     * Additional base features for a custom serializer for all implementations of a particular interface or super class.
     */
    abstract class Implements<T : Any>(clazz: Class<T>, revealSubClassesInSchema: Boolean)
        : CustomSerializerImpl<T>(clazz, revealSubClassesInSchema, withInheritance = true) {
        constructor(clazz: Class<T>) : this(clazz, revealSubClassesInSchema = false)
    }

    /**
     * Additional base features over and above [Implements] or [Is] custom serializer for when the serialized form should be
     * the serialized form of a proxy class, and the object can be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    class Proxy<T : Any, P : Any>(
        private val serializer: InternalCustomSerializer<T, P>,
        factory: SerializerFactory
    ) : CustomSerializer<T>() {
        override val revealSubclassesInSchema: Boolean
            get() = serializer.revealSubclasses

        private val proxySerializer: ObjectSerializer by lazy {
            ObjectSerializer.make(factory.getTypeInformation(serializer.proxyType), factory)
        }

        override val type: Class<T> = serializer.type
        override val typeDescriptor: Symbol = typeDescriptorFor(type)
        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        override fun isSerializerFor(clazz: Class<*>): Boolean {
            return if (serializer.withInheritance) {
                type.isAssignableFrom(clazz)
            } else {
                type === clazz
            }
        }

        override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}

        override fun writeDescribedObject(
            obj: T,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            val proxy = serializer.toProxy(obj, context)
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
            @Suppress("unchecked_cast")
            val proxy = proxySerializer.readObject(obj, serializationSchemas, metadata, input, context) as P
            return serializer.fromProxy(proxy, context)
        }

        override fun toString(): String = "${this::class.java.simpleName}(${serializer::class.java.name})"
    }

    /**
     * A custom serializer where the on-wire representation is a string. For example, a [Currency][java.util.Currency]
     * might be represented as a 3 character currency code, and converted to and from that string. By default, it is
     * assumed that the [toString] method will generate the string representation and that there is a constructor that
     * takes such a string as an argument to reconstruct.
     *
     * @param clazz The type to be marshalled
     * @param withInheritance Whether subclasses of the class can also be marshalled.
     * @param maker A lambda for constructing an instance, that defaults to calling a constructor that expects a string.
     * @param unmaker A lambda that extracts the string value for an instance, that defaults to the [toString] method.
     */
    abstract class ToString<T : Any>(clazz: Class<T>,
                                     withInheritance: Boolean = false,
                                     private val maker: (String) -> T = clazz.getConstructor(String::class.java).let { ctor ->
                                         { string -> ctor.newInstance(string) }
                                     },
                                     private val unmaker: (T) -> String = Any::toString)
        : CustomSerializerImpl<T>(clazz, revealSubclassesInSchema = false, withInheritance) {

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext) {
            data.putString(unmaker(obj))
        }

        override fun readObject(
            obj: Any,
            serializationSchemas: SerializationSchemas,
            metadata: Metadata,
            input: DeserializationInput,
            context: SerializationContext
        ): T {
            val proxy = obj as String
            return maker(proxy)
        }
    }
}
