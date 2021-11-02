package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.FingerprintWriter
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
interface CustomSerializer<T : Any> : AMQPSerializer<T>, SerializerFor {
    /**
     * This is a collection of custom serializers that this custom serializer depends on.  e.g. for proxy objects
     * that refer to other custom types etc.
     */
    val additionalSerializers: Iterable<CustomSerializer<out Any>>

    /**
     * This custom serializer is also allowed to deserialize these classes. This allows us
     * to deserialize objects into completely different types, e.g. `A` -> `sandbox.A`.
     */
    val deserializationAliases: Set<TypeIdentifier>

    /**
     * This exists purely for documentation and cross-platform purposes.
     * It is not used by our serialization / deserialization code path.
     */
    val schemaForDocumentation: Schema

    override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    )

    fun writeDescribedObject(
        obj: T,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext
    )

    /**
     * [CustomSerializerRegistry.findCustomSerializer] will invoke this method on the [CustomSerializer]
     * that it selects to give that serializer an opportunity to customise its behaviour. The serializer
     * can also return `null` here, in which case [CustomSerializerRegistry] will proceed as if no
     * serializer is available for [declaredType].
     */
    fun specialiseFor(declaredType: Type): AMQPSerializer<T>? = this

    abstract class CustomSerializerBase<T : Any> : CustomSerializer<T> {
        override val revealSubclassesInSchema: Boolean
            get() = false
        override val additionalSerializers: Iterable<CustomSerializer<out Any>>
            get() = emptyList()
        override val deserializationAliases: Set<TypeIdentifier>
            get() = emptySet()
        protected abstract val descriptor: Descriptor

        override fun writeObject(
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
    }

    /**
     * This custom serializer represents a sort of symbolic link from a subclass to a super class, where the super
     * class custom serializer is responsible for the "on the wire" format but we want to create a reference to the
     * subclass in the schema, so that we can distinguish between subclasses.
     */
    // TODO should this be a custom serializer at all, or should it just be a plain AMQPSerializer?
    class SubClass<T : Any>(
        private val clazz: Class<*>,
        private val superClassSerializer: CustomSerializer<T>
    ) : CustomSerializerBase<T>() {
        // TODO should this be empty or contain the schema of the super?
        override val schemaForDocumentation = Schema(emptyList())

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz === this.clazz

        override val type: Type get() = clazz

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
    }

    /**
     * Additional base features for a custom serializer for a particular class [withInheritance] is false
     * or super class / interfaces [withInheritance] is true
     */
    abstract class CustomSerializerImpl<T : Any>(
        protected val clazz: Class<T>,
        protected val withInheritance: Boolean
    ) : CustomSerializerBase<T>() {
        override val type: Type get() = clazz
        override val typeDescriptor: Symbol = typeDescriptorFor(clazz)
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
        override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}
        override fun isSerializerFor(clazz: Class<*>): Boolean {
            return if (withInheritance) {
                this.clazz.isAssignableFrom(clazz)
            } else {
                this.clazz === clazz
            }
        }
    }

    /**
     * Additional base features for a custom serializer for a particular class, that excludes subclasses.
     */
    abstract class Is<T : Any>(clazz: Class<T>) : CustomSerializerImpl<T>(clazz, false)

    /**
     * Additional base features for a custom serializer for all implementations of a particular interface or super class.
     */
    abstract class Implements<T : Any>(clazz: Class<T>) : CustomSerializerImpl<T>(clazz, true)

    /**
     * Additional base features over and above [Implements] or [Is] custom serializer for when the serialized form should be
     * the serialized form of a proxy class, and the object can be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    abstract class Proxy<T : Any, P : Any>(clazz: Class<T>,
                                           protected val proxyClass: Class<P>,
                                           protected val factory: LocalSerializerFactory,
                                           withInheritance: Boolean)
        : CustomSerializerImpl<T>(clazz, withInheritance) {

        private val proxySerializer: ObjectSerializer by lazy {
            ObjectSerializer.make(factory.getTypeInformation(proxyClass), factory)
        }

        override val schemaForDocumentation: Schema by lazy {
            val typeNotations = mutableSetOf<TypeNotation>(
                CompositeType(
                    AMQPTypeIdentifiers.nameForType(type),
                    null,
                    emptyList(),
                    descriptor, proxySerializer.fields))
            for (additional in additionalSerializers) {
                typeNotations.addAll(additional.schemaForDocumentation.types)
            }
            Schema(typeNotations.toList())
        }

        /**
         * Implement these two methods.
         */
        protected abstract fun toProxy(obj: T): P

        protected abstract fun fromProxy(proxy: P): T

        /**
         * These two methods can be overridden, if necessary.
         */
        protected open fun toProxy(obj: T, context: SerializationContext): P = toProxy(obj)
        protected open fun fromProxy(proxy: P, context: SerializationContext): T = fromProxy(proxy)

        override fun writeDescribedObject(
            obj: T,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            val proxy = toProxy(obj, context)
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
            return fromProxy(proxy, context)
        }
    }

    /**
     * A custom serializer where the on-wire representation is a string.  For example, a [Currency] might be represented
     * as a 3 character currency code, and converted to and from that string.  By default, it is assumed that the
     * [toString] method will generate the string representation and that there is a constructor that takes such a
     * string as an argument to reconstruct.
     *
     * @param clazz The type to be marshalled
     * @param withInheritance Whether subclasses of the class can also be marshalled.
     * @param maker A lambda for constructing an instance, that defaults to calling a constructor that expects a string.
     * @param unmaker A lambda that extracts the string value for an instance, that defaults to the [toString] method.
     */
    abstract class ToString<T : Any>(clazz: Class<T>, withInheritance: Boolean = false,
                                     private val maker: (String) -> T = clazz.getConstructor(String::class.java).let { ctor ->
                                         { string -> ctor.newInstance(string) }
                                     },
                                     private val unmaker: (T) -> String = Any::toString)
        : CustomSerializerImpl<T>(clazz, withInheritance) {

        override val schemaForDocumentation = Schema(
            listOf(RestrictedType(AMQPTypeIdentifiers.nameForType(type), "", listOf(AMQPTypeIdentifiers.nameForType(type)),
                AMQPTypeIdentifiers.primitiveTypeName(String::class.java),
                descriptor, emptyList())))

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
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
