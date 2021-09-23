package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.FingerprintWriter
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

interface SerializerFor {
    /**
     * This method should return true if the custom serializer can serialize an instance of the class passed as the
     * parameter.
     */
    fun isSerializerFor(clazz: Class<*>): Boolean

    val revealSubclassesInSchema: Boolean
}

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T : Any> : AMQPSerializer<T>, SerializerFor {

    protected abstract val descriptor: Descriptor

    /**
     * This exists purely for documentation and cross-platform purposes. It is not used by our serialization / deserialization
     * code path.
     */
    abstract val schemaForDocumentation: Schema

    /**
     * Whether subclasses using this serializer via inheritance should have a mapping in the schema.
     */
    override val revealSubclassesInSchema: Boolean get() = false

    override fun writeObject(
        obj: Any, data: Data, type: Type, output: SerializationOutput,
        context: SerializationContext, debugIndent: Int
    ) {
        data.withDescribed(descriptor) {
            writeDescribedObject(uncheckedCast(obj), data, type, output, context)
        }
    }

    abstract fun writeDescribedObject(
        obj: T, data: Data, type: Type, output: SerializationOutput,
        context: SerializationContext
    )

    /**
     * [CustomSerializerRegistry.findCustomSerializer] will invoke this method on the [CustomSerializer]
     * that it selects to give that serializer an opportunity to customise its behaviour. The serializer
     * can also return `null` here, in which case [CustomSerializerRegistry] will proceed as if no
     * serializer is available for [declaredType].
     */
    open fun specialiseFor(declaredType: Type): AMQPSerializer<T>? = this

    /**
     * This custom serializer represents a sort of symbolic link from a subclass to a super class, where the super
     * class custom serializer is responsible for the "on the wire" format but we want to create a reference to the
     * subclass in the schema, so that we can distinguish between subclasses.
     */
    // TODO: should this be a custom serializer at all, or should it just be a plain AMQPSerializer?
    class SubClass<T : Any>(private val clazz: Class<*>, private val superClassSerializer: CustomSerializer<T>) :
        CustomSerializer<T>() {
        // TODO: should this be empty or contain the schema of the super?
        override val schemaForDocumentation = Schema(emptyList())

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz

        override val type: Type get() = clazz

        override val typeDescriptor: Symbol by lazy {
            val fingerprint = FingerprintWriter()
                .write(superClassSerializer.typeDescriptor)
                .write(AMQPTypeIdentifiers.nameForType(clazz))
                .fingerprint
            Symbol.valueOf("$DESCRIPTOR_DOMAIN:$fingerprint")
        }

        private val typeNotation: TypeNotation = RestrictedType(
            AMQPTypeIdentifiers.nameForType(clazz),
            null,
            emptyList(),
            AMQPTypeIdentifiers.nameForType(superClassSerializer.type),
            Descriptor(typeDescriptor),
            emptyList()
        )

        override fun writeClassInfo(output: SerializationOutput) {
            output.writeTypeNotations(typeNotation)
        }

        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        override fun writeDescribedObject(
            obj: T, data: Data, type: Type, output: SerializationOutput,
            context: SerializationContext
        ) {
            superClassSerializer.writeDescribedObject(obj, data, type, output, context)
        }

        override fun readObject(
            obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
            input: DeserializationInput, context: SerializationContext
        ): T {
            return superClassSerializer.readObject(obj, serializationSchemas, metadata, input, context)
        }
    }

    /**
     * Additional base features for a custom serializer for a particular class [withInheritance] is false
     * or super class / interfaces [withInheritance] is true
     */
    abstract class CustomSerializerImp<T : Any>(protected val clazz: Class<T>, protected val withInheritance: Boolean) :
        CustomSerializer<T>() {
        override val type: Type get() = clazz
        override val typeDescriptor: Symbol = typeDescriptorFor(clazz)
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
        override fun isSerializerFor(clazz: Class<*>): Boolean =
            if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
    }

    /**
     * Base features for when the serialized form should be the serialized form of a proxy class, and the object can
     * be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    abstract class Proxy<T : Any, P : Any>(
        clazz: Class<T>,
        protected val proxyClass: Class<P>,
        protected val factory: LocalSerializerFactory,
        withInheritance: Boolean = true
    ) : CustomSerializerImp<T>(clazz, withInheritance) {
        override fun isSerializerFor(clazz: Class<*>): Boolean =
            if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz

        private val proxySerializer: ObjectSerializer by lazy {
            ObjectSerializer.make(
                factory.getTypeInformation(
                    proxyClass
                ), factory
            )
        }

        override val schemaForDocumentation: Schema by lazy {
            val typeNotations = mutableSetOf<TypeNotation>(
                CompositeType(
                    AMQPTypeIdentifiers.nameForType(type),
                    null,
                    emptyList(),
                    descriptor, proxySerializer.fields
                )
            )
            Schema(typeNotations.toList())
        }

        /**
         * Implement these two methods.
         */
        protected abstract fun toProxy(obj: T): P

        protected abstract fun fromProxy(proxy: P): T

        override fun writeDescribedObject(
            obj: T, data: Data, type: Type, output: SerializationOutput,
            context: SerializationContext
        ) {
            val proxy = toProxy(obj)
            data.withList {
                proxySerializer.propertySerializers.forEach { (_, serializer) ->
                    serializer.writeProperty(proxy, this, output, context, 0)
                }
            }
        }

        override fun readObject(
            obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
            input: DeserializationInput, context: SerializationContext
        ): T {
            val proxy: P =
                uncheckedCast(proxySerializer.readObject(obj, serializationSchemas, metadata, input, context))
            return fromProxy(proxy)
        }
    }
}
